package org.blackaddons.api.util.color

import java.awt.Color
import kotlin.math.roundToInt

/** [t] clamped to 0..1; alpha interpolated too. */
fun lerpColor(a: Color, b: Color, t: Float): Color {
    val ct = t.coerceIn(0f, 1f)
    return Color(
        (a.red + (b.red - a.red) * ct).toInt().coerceIn(0, 255),
        (a.green + (b.green - a.green) * ct).toInt().coerceIn(0, 255),
        (a.blue + (b.blue - a.blue) * ct).toInt().coerceIn(0, 255),
        (a.alpha + (b.alpha - a.alpha) * ct).toInt().coerceIn(0, 255),
    )
}

/**
 * Multiplies the RGB channels by [factor] and clamps them. Alpha stays the same. A factor below 1 darkens and above 1
 * brightens.
 */
fun Color.scaled(factor: Float): Color = Color(
    (red * factor).roundToInt().coerceIn(0, 255),
    (green * factor).roundToInt().coerceIn(0, 255),
    (blue * factor).roundToInt().coerceIn(0, 255),
    alpha,
)

/** Packed [argb] with its alpha scaled by [alpha] (0..1). */
fun fade(argb: Int, alpha: Float): Int =
    if (alpha >= 1f) argb else (((argb ushr 24) * alpha.coerceAtLeast(0f)).toInt() shl 24) or (argb and RGB)

/** Packed ARGB per channel, [t] clamped to 0..1. */
internal fun lerpArgb(a: Int, b: Int, t: Float): Int {
    val ct = t.coerceIn(0f, 1f)
    return lerpChannel(a, b, ct, 24) or lerpChannel(a, b, ct, 16) or lerpChannel(a, b, ct, 8) or lerpChannel(a, b, ct, 0)
}

private fun lerpChannel(a: Int, b: Int, t: Float, shift: Int): Int {
    val from = (a ushr shift) and 0xFF
    val to = (b ushr shift) and 0xFF
    return (from + (to - from) * t).toInt().coerceIn(0, 255) shl shift
}

private const val RGB = 0xFFFFFF
