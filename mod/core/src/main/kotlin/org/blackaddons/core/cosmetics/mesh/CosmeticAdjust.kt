package org.blackaddons.core.cosmetics.mesh
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.core.cosmetics.store.CosmeticJson

internal data class AdjustAxis(val id: String, val min: Double, val max: Double) {

    val neutral: Double get() = if (id == CosmeticAdjust.SCALE) 1.0 else 0.0

    /** False means the range always moves the cosmetic. */
    val neutralInRange: Boolean get() = neutral in min..max
}

/**
 * Player nudges to a cosmetic's placement (e.g. to clear a helmet), within the definition limits. Only resolved locally
 * for preview. Same as the backend helper: offsets add, scale multiplies, values clamp.
 */
internal object CosmeticAdjust {

    /** Holds both the limits and the player's choice. */
    const val KEY = "adjust"

    const val SCALE = "scale"

    private const val ATTACH = "attach"
    private const val OFFSET = "offset"

    private val OFFSETS = listOf("offsetX", "offsetY", "offsetZ")

    /** Fixed order, so controls don't jump around. */
    val AXES: List<String> = OFFSETS + SCALE

    fun limits(definition: JsonObject): List<AdjustAxis> {
        val declared = definition.get(KEY) as? JsonObject ?: return emptyList()
        return AXES.mapNotNull { id ->
            val range = declared.get(id) as? JsonObject ?: return@mapNotNull null
            val min = number(range, "min") ?: return@mapNotNull null
            val max = number(range, "max") ?: return@mapNotNull null
            if (min > max) null else AdjustAxis(id, min, max)
        }
    }

    fun choice(stored: JsonObject?): Map<String, Double> {
        if (stored == null) return emptyMap()
        val out = LinkedHashMap<String, Double>()
        for (id in AXES) number(stored, id)?.let { out[id] = it }
        return out
    }

    /**
     * Merges [choice] into `attach` and removes the limits, same as the backend manifest. Clamped again to [limits].
     * Axes that aren't offered anymore go back to neutral.
     */
    fun apply(resolved: JsonObject, choice: Map<String, Double>, limits: List<AdjustAxis>) {
        resolved.remove(KEY)
        if (choice.isEmpty() || limits.isEmpty()) return

        val allowed = limits.associateBy { it.id }
        val attach = resolved.get(ATTACH) as? JsonObject
            ?: JsonObject().also { resolved.add(ATTACH, it) }

        val offsets = DoubleArray(3)
        var moved = false
        for ((index, id) in OFFSETS.withIndex()) {
            val axis = allowed[id] ?: continue
            offsets[index] = choice[id]?.coerceIn(axis.min, axis.max) ?: continue
            moved = true
        }
        if (moved) addOffsets(attach, offsets)

        allowed[SCALE]?.let { axis ->
            val factor = choice[SCALE]?.coerceIn(axis.min, axis.max) ?: return@let
            attach.addProperty(SCALE, currentScale(attach) * factor)
        }
    }

    /** Into `attach.offset` `[x, y, z]`, the shape [CosmeticJson] reads. */
    private fun addOffsets(attach: JsonObject, deltas: DoubleArray) {
        val current = attach.get(OFFSET) as? JsonArray
        val out = JsonArray(3)
        for (i in 0..2) out.add(numberAt(current, i) + deltas[i])
        attach.add(OFFSET, out)
    }

    private fun currentScale(attach: JsonObject): Double = number(attach, SCALE) ?: 1.0

    private fun numberAt(array: JsonArray?, index: Int): Double =
        array?.takeIf { index < it.size() }?.get(index)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: 0.0

    private fun number(owner: JsonObject, key: String): Double? =
        owner.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
}
