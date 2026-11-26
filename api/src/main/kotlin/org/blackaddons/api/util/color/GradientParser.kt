package org.blackaddons.api.util.color


/**
 * Parses CSS `linear-gradient`/`radial-gradient` stops (`rgba(...) N%`). The angle is ignored - text
 * gradients are always a horizontal sweep.
 */
object GradientParser {
    private val STOP = Regex("""rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*[^)]*\)\s*(\d+)%""")

    fun parse(css: String?): List<ColorStop> {
        if (css.isNullOrBlank()) return emptyList()
        if (!css.startsWith("linear-gradient") && !css.startsWith("radial-gradient")) return emptyList()
        return STOP.findAll(css)
            .map { m ->
                val (r, g, b, pct) = m.destructured
                val rgb = (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
                ColorStop(rgb, pct.toFloat() / 100f)
            }
            .sortedBy { it.fraction }
            .toList()
    }

    /** Inverse of [parse], in the backend's stored form (`90deg`, full alpha). */
    fun toCss(stops: List<ColorStop>): String {
        val body = stops.sortedBy { it.fraction }.joinToString(", ") { stop ->
            val r = (stop.rgb shr 16) and 0xFF
            val g = (stop.rgb shr 8) and 0xFF
            val b = stop.rgb and 0xFF
            val pct = (stop.fraction * 100f).toInt().coerceIn(0, 100)
            "rgba($r, $g, $b, 1) $pct%"
        }
        return "linear-gradient(90deg, $body)"
    }
}
