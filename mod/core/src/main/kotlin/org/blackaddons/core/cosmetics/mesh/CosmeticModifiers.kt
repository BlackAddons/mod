package org.blackaddons.core.cosmetics.mesh
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.cosmetics.Cosmetic3dItem
import org.blackaddons.api.util.io.str
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.cosmetics.store.CosmeticJson

/**
 * Local copy of the backend resolve, so the wardrobe can preview choices. It has to match the backend exactly: axes in
 * declaration order (later wins), unknown axis or option -> default, `modifiers` dropped, player tints merged per slot.
 */
internal object CosmeticModifiers {

    /** Null if there's no usable model. Maps can be empty to preview defaults. */
    fun previewItem(
        entry: Cosmetic3dEntry,
        choices: Map<String, String>,
        tints: Map<String, String>,
        adjust: Map<String, Double> = emptyMap(),
    ): Cosmetic3dItem? {
        val resolved = apply(entry.definition, choices)
        mergeTints(resolved, tints)
        // After the axes, because an option can swap in its own attach block.
        CosmeticAdjust.apply(resolved, adjust, entry.adjustAxes)
        attachUrls(resolved)
        return CosmeticJson.itemFromDefinition(resolved, entry.id)
    }

    fun apply(definition: JsonObject, choices: Map<String, String>): JsonObject {
        val out = definition.deepCopy()
        val raw = out.remove("modifiers") as? JsonArray ?: return out
        val axes = Cosmetic3dEntry.parseAxes(raw)

        // Resolve every choice first, because conditions can reference later axes.
        val chosen = axes.associate { axis -> axis.id to (choices[axis.id] ?: axis.default) }

        for (axis in axes) {
            if (!axis.isActive(chosen)) continue
            val declared = raw.objects().firstOrNull { it.str("id") == axis.id } ?: continue
            val options = (declared.get("options") as? JsonArray)?.objects() ?: continue
            val option = options.firstOrNull { it.str("id") == chosen[axis.id] }
                ?: options.firstOrNull { it.str("id") == axis.default }
                ?: continue

            (option.get("model") as? JsonObject)?.let { out.add("model", it.deepCopy()) }
            option.get("clip")?.takeIf { !it.isJsonNull }?.let { out.add("clip", it.deepCopy()) }
            applyLayerOps(out, option)
        }
        return out
    }

    fun mergeTints(resolved: JsonObject, tints: Map<String, String>) {
        if (tints.isEmpty()) return
        val slots = resolved.get("tints") as? JsonObject ?: JsonObject().also { resolved.add("tints", it) }
        for ((slot, hex) in tints) slots.addProperty(slot, hex)
    }

    /** The one step the catalog leaves to us: `/v1/me/cosmetics3d` sends definitions without URLs. */
    fun attachUrls(node: JsonObject) {
        node.str("sha256")?.let { node.addProperty("url", "${Backend.ASSETS_URL}/$it") }
        for ((_, value) in node.entrySet()) when (value) {
            is JsonObject -> attachUrls(value)
            is JsonArray -> value.forEach { if (it is JsonObject) attachUrls(it) }
            else -> {}
        }
    }

    /** Overrides a layer by stable `id` or adds one. An added layer's `tintSlot` gives a cosmetic extra colors. */
    private fun applyLayerOps(item: JsonObject, option: JsonObject) {
        // Old field the backend still reads: overrides every base-role layer.
        (option.get("baseTexture") as? JsonObject)?.str("sha256")?.let { sha ->
            layersOf(item).filter { (it.str("role") ?: "base") == "base" }
                .forEach { it.addProperty("sha256", sha) }
        }

        val ops = option.get("layerOps") as? JsonArray ?: return
        for (op in ops.objects()) when (op.str("op")) {
            "override" -> overrideLayer(item, op)
            "add" -> addLayer(item, op)
        }
    }

    private fun overrideLayer(item: JsonObject, op: JsonObject) {
        val target = op.str("targetLayerId") ?: return
        for (layer in layersOf(item)) {
            if (layer.str("id") != target) continue
            (op.get("asset") as? JsonObject)?.str("sha256")?.let { layer.addProperty("sha256", it) }
            // Null means untinted. Absent means don't change it.
            if (op.has("tintSlot")) {
                val slot = op.str("tintSlot")
                if (slot != null) layer.addProperty("tintSlot", slot) else layer.remove("tintSlot")
            }
        }
    }

    private fun addLayer(item: JsonObject, op: JsonObject) {
        val added = op.get("layer") as? JsonObject ?: return
        val layers = item.get("layers") as? JsonArray ?: JsonArray().also { item.add("layers", it) }
        layers.add(added.deepCopy())
    }

    private fun layersOf(item: JsonObject): List<JsonObject> =
        (item.get("layers") as? JsonArray)?.objects().orEmpty()

    private fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()
}
