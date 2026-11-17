package org.blackaddons.core.cosmetics.mesh
import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal data class ModifierOption(val id: String, val label: String)

/** Only applies while [axis] is on one of [options] (e.g. glow needs a tip). */
internal data class AxisCondition(val axis: String, val options: List<String>)

internal data class ModifierAxis(
    val id: String,
    val label: String,
    val default: String,
    val options: List<ModifierOption>,
    val condition: AxisCondition? = null,
) {

    /** The one rule for whether the axis applies. Resolve and the wardrobe both use it. */
    fun isActive(chosen: Map<String, String>): Boolean {
        val condition = condition ?: return true
        return chosen[condition.axis] in condition.options
    }
}

/**
 * A `/v1/me/cosmetics3d` catalog entry. [definition] is the admin's fragment as is, without asset URLs. Locked entries
 * show as a showcase. [tints] and [modifiers] stay in wire form, so they round-trip unchanged.
 */
internal data class Cosmetic3dEntry(
    val id: String,
    val name: String,
    val definition: JsonObject,
    val unlocked: Boolean,
    val equipped: Boolean,
    /** Slot -> `#RRGGBB(AA)`. Empty means defaults. */
    val tints: Map<String, String> = emptyMap(),
    /** Axis -> option. Missing means default. */
    val modifiers: Map<String, String> = emptyMap(),
    /** Axis -> nudge. Missing means as authored. */
    val adjust: Map<String, Double> = emptyMap(),
) {

    val axes: List<ModifierAxis> by lazy { parseAxes(definition.get("modifiers") as? JsonArray) }
    val adjustAxes: List<AdjustAxis> by lazy { CosmeticAdjust.limits(definition) }

    companion object {

        fun parseList(arr: JsonArray): List<Cosmetic3dEntry> =
            arr.mapNotNull { (it as? JsonObject)?.let(::parse) }

        private fun parse(o: JsonObject): Cosmetic3dEntry? {
            val id = string(o, "id") ?: return null
            val definition = o.get("definition") as? JsonObject ?: return null
            return Cosmetic3dEntry(
                id = id,
                name = string(o, "name") ?: id,
                definition = definition,
                unlocked = bool(o, "unlocked"),
                equipped = bool(o, "equipped"),
                tints = stringMap(o.get("tints") as? JsonObject),
                modifiers = stringMap(o.get("modifiers") as? JsonObject),
                adjust = CosmeticAdjust.choice(o.get(CosmeticAdjust.KEY) as? JsonObject),
            )
        }

        fun parseAxes(arr: JsonArray?): List<ModifierAxis> {
            if (arr == null) return emptyList()
            val out = ArrayList<ModifierAxis>(arr.size())
            for (el in arr) {
                val o = el as? JsonObject ?: continue
                val id = string(o, "id") ?: continue
                val options = parseOptions(o.get("options") as? JsonArray)
                if (options.isEmpty()) continue
                // A default that names no option uses the first option, like the backend.
                val declared = string(o, "default")
                val default = options.firstOrNull { it.id == declared }?.id ?: options.first().id
                out.add(ModifierAxis(id, string(o, "label") ?: id, default, options, parseCondition(o)))
            }
            return out
        }

        private fun parseCondition(axis: JsonObject): AxisCondition? {
            val condition = axis.get("condition") as? JsonObject ?: return null
            val on = string(condition, "axis") ?: return null
            val options = (condition.get("options") as? JsonArray)
                ?.mapNotNull { it.takeIf { v -> v.isJsonPrimitive }?.asString }
                .orEmpty()
            return AxisCondition(on, options)
        }

        private fun parseOptions(arr: JsonArray?): List<ModifierOption> {
            if (arr == null) return emptyList()
            val out = ArrayList<ModifierOption>(arr.size())
            for (el in arr) {
                val o = el as? JsonObject ?: continue
                val id = string(o, "id") ?: continue
                out.add(ModifierOption(id, string(o, "label") ?: id))
            }
            return out
        }

        private fun stringMap(o: JsonObject?): Map<String, String> {
            if (o == null) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for ((k, v) in o.entrySet()) {
                val s = v?.takeIf { !it.isJsonNull }?.asString ?: continue
                out[k] = s
            }
            return out
        }

        private fun string(o: JsonObject, key: String): String? =
            o.get(key)?.takeIf { !it.isJsonNull }?.asString

        private fun bool(o: JsonObject, key: String): Boolean =
            o.get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: false
    }
}
