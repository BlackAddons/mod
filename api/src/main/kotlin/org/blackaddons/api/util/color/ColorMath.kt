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
