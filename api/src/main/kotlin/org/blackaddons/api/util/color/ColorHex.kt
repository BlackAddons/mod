package org.blackaddons.api.util.color
import java.awt.Color

/** `#RRGGBB` or `#RRGGBBAA` (alpha last) - the one hex format for themes and settings. */
fun Color.toHex(withAlpha: Boolean = true): String =
    if (withAlpha) "#%02X%02X%02X%02X".format(red, green, blue, alpha)
    else "#%02X%02X%02X".format(red, green, blue)

/** Hash optional. */
fun String.toColorOrNull(): Color? {
    val digits = trim().removePrefix("#")
    val parsed = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> Color(
            ((parsed shr 16) and 0xFF).toInt(),
            ((parsed shr 8) and 0xFF).toInt(),
            (parsed and 0xFF).toInt(),
        )
        8 -> Color(
            ((parsed shr 24) and 0xFF).toInt(),
            ((parsed shr 16) and 0xFF).toInt(),
            ((parsed shr 8) and 0xFF).toInt(),
            (parsed and 0xFF).toInt(),
        )
        else -> null
    }
}
