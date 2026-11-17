package org.blackaddons.api.util.color
import org.blackaddons.api.util.color.GradientColors.animatedGradientColor
import org.blackaddons.api.util.color.GradientColors.chromaColor
import org.blackaddons.api.util.color.GradientColors.colorAt
import org.blackaddons.api.util.color.GradientColors.perChar
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Multi-stop gradient math, shared by HUD text and `Component` colors so every surface animates identically.
 * [perChar]/[colorAt] are static; [chromaColor]/[animatedGradientColor] are recomputed each frame.
 */
object GradientColors {
    /** Half a hue turn across the name, matching the frontend. */
    const val CHROMA_HUE_SPREAD = 0.5f

    /** Sampled evenly across [stops]. */
    fun perChar(length: Int, stops: List<ColorStop>): IntArray {
        if (length <= 0) return IntArray(0)
        if (stops.isEmpty()) return IntArray(length) { 0xFFFFFF }
        return IntArray(length) { i ->
            colorAt(stops, if (length > 1) i.toFloat() / (length - 1) else 0f)
        }
    }

    /** [frac] in 0..1. */
    fun colorAt(stops: List<ColorStop>, frac: Float): Int {
        if (stops.isEmpty()) return 0xFFFFFF
        if (stops.size == 1) return stops[0].rgb
        var lo = stops.first()
        var hi = stops.last()
        for (j in 0 until stops.size - 1) {
            if (frac >= stops[j].fraction && frac <= stops[j + 1].fraction) {
                lo = stops[j]; hi = stops[j + 1]; break
            }
        }
        if (lo.fraction == hi.fraction) return lo.rgb
        val t = ((frac - lo.fraction) / (hi.fraction - lo.fraction)).coerceIn(0f, 1f)
        val r = lerp((lo.rgb shr 16) and 0xFF, (hi.rgb shr 16) and 0xFF, t)
        val g = lerp((lo.rgb shr 8) and 0xFF, (hi.rgb shr 8) and 0xFF, t)
        val b = lerp(lo.rgb and 0xFF, hi.rgb and 0xFF, t)
        return (r shl 16) or (g shl 8) or b
    }

    @JvmStatic
    fun chromaColor(speed: Float, offset: Float, timeSeconds: Double): Int {
        val time = timeSeconds * speed / 3.0
        var hue = ((time + offset) % 1.0).toFloat()
        if (hue < 0f) hue += 1f
        return Color.HSBtoRGB(hue, 1f, 1f) and 0xFFFFFF
    }

    /** Ping-pongs over time (seamless on non-cyclic gradients), shifted by [offset]. */
    @JvmStatic
    fun animatedGradientColor(stops: List<ColorStop>, speed: Float, offset: Float, timeSeconds: Double): Int {
        if (stops.isEmpty()) return 0xFFFFFF
        if (stops.size == 1) return stops[0].rgb
        // Range 2 at 2/3 units/s: one round trip takes 3/speed s, the frontend's period.
        val time = timeSeconds * speed * (2.0 / 3.0)
        var ratio = (time + offset) % 2.0
        if (ratio < 0) ratio += 2.0
        val finalRatio = (if (ratio > 1.0) 2.0 - ratio else ratio).toFloat()
        return colorAt(stops, finalRatio)
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + t * (b - a)).roundToInt()
}
