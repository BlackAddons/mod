package org.blackaddons.core.cosmetics

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.util.color.ColorStop
import kotlin.math.roundToInt

/**
 * Backend gradient `{ "stops": [ { "pos": 0.0, "rgb": 3352640 } ] }`. Replaces the old CSS string (the addon `Render`
 * API still uses it). Sorted just to be safe.
 */
internal object GradientStops {
    fun parse(gradient: JsonObject?): List<ColorStop> {
        val stops = gradient?.get("stops") as? JsonArray ?: return emptyList()
        val out = ArrayList<ColorStop>(stops.size())
        for (element in stops) {
            val stop = element as? JsonObject ?: continue
            val rgb = num(stop, "rgb")?.toInt()?.and(0xFFFFFF) ?: continue
            val pos = num(stop, "pos")?.toFloat() ?: continue
            out.add(ColorStop(rgb, pos))
        }
        out.sortBy { it.fraction }
        return out
    }

    const val MIN_STOPS = 2
    const val MAX_STOPS = 32

    private const val GRID = 1000

    fun toJson(stops: List<ColorStop>): JsonObject {
        val arr = JsonArray(stops.size)
        for (stop in strictlyAscending(stops)) {
            arr.add(JsonObject().apply {
                addProperty("pos", stop.fraction)
                addProperty("rgb", stop.rgb and 0xFFFFFF)
            })
        }
        return JsonObject().apply { add("stops", arr) }
    }

    /**
     * Sorted, with equal positions pushed one whole grid step apart (the backend requires strictly ascending). Overflow
     * packs back from 1. Whole steps, because epsilon plus rounding can undo the nudge.
     */
    fun strictlyAscending(stops: List<ColorStop>): List<ColorStop> {
        val sorted = stops.sortedBy { it.fraction }
        val steps = IntArray(sorted.size) { (sorted[it].fraction.coerceIn(0f, 1f) * GRID).roundToInt() }
        for (i in 1 until steps.size) steps[i] = maxOf(steps[i], steps[i - 1] + 1)
        if (steps.isNotEmpty() && steps.last() > GRID) {
            steps[steps.lastIndex] = GRID
            for (i in steps.lastIndex - 1 downTo 0) steps[i] = minOf(steps[i], steps[i + 1] - 1)
        }
        return sorted.mapIndexed { i, stop -> ColorStop(stop.rgb, steps[i].toFloat() / GRID) }
    }

    private fun num(o: JsonObject, key: String): Number? =
        o.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asNumber
}
