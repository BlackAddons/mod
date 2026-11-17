package org.blackaddons.core.cosmetics

/** Wire `#RRGGBB(AA)` (alpha last) <-> ARGB int (alpha first). Alpha only matters on layers with `supportsAlpha`. */
internal object TintHex {

    fun parse(value: String): Int? {
        val hex = value.removePrefix("#")
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.toInt()?.let { 0xFF000000.toInt() or it }
            8 -> hex.toLongOrNull(16)?.let { rgba ->
                val rgb = (rgba ushr 8).toInt() and 0xFFFFFF
                val alpha = (rgba and 0xFF).toInt()
                (alpha shl 24) or rgb
            }
            else -> null
        }
    }

    fun format(argb: Int, withAlpha: Boolean): String {
        val rgb = argb and 0xFFFFFF
        if (!withAlpha) return "#%06x".format(rgb)
        return "#%06x%02x".format(rgb, (argb ushr 24) and 0xFF)
    }
}
