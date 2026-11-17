package org.blackaddons.core.ui.tab.eggs

import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.GradientColors
import org.blackaddons.api.util.graphemes
import java.awt.Color

/** His own joke about himself, in his own colors and bold like he writes it. */
internal object NoammEgg {

    fun draw(name: String, x: Float, y: Float, progress: Float, color: Color): Float =
        swap(name, NOAMM, x, y, progress, color, NOAMM_STOPS, Fonts.bold)

    /**
     * The old name is gone by halfway before the new one arrives: cross-fading on one baseline reads as a render bug.
     * The returned width interpolates so neighbors slide.
     */
    private fun swap(
        from: String, to: String, x: Float, y: Float, progress: Float,
        color: Color, stops: List<ColorStop>, toFont: Font,
    ): Float {
        val leaving = (1f - progress * 2f).coerceIn(0f, 1f)
        val arriving = (progress * 2f - 1f).coerceIn(0f, 1f)

        if (leaving > 0f) {
            var cx = x
            for (glyph in from.graphemes()) cx += glyph(glyph, cx, y, color.withAlpha((255 * leaving).toInt()))
        }
        if (arriving > 0f) {
            val glyphs = to.graphemes()
            val ramp = GradientColors.perChar(glyphs.size, stops)
            var cx = x
            for ((i, glyph) in glyphs.withIndex()) {
                cx += glyph(glyph, cx, y, Color(ramp[i]).withAlpha((255 * arriving).toInt()), toFont)
            }
        }

        val fromW = Render.textWidth(from, Theme.FONT_BODY, Fonts.medium)
        val toW = Render.textWidth(to, Theme.FONT_BODY, toFont)
        return fromW + (toW - fromW) * progress
    }

    /** Not translated: it is a phrase of his rather than a string of ours. */
    private const val NOAMM = "Tel Aviv Impressed"

    private val NOAMM_STOPS = listOf(ColorStop(0x3D6F86, 0f), ColorStop(0xCF2A5A, 1f))
}
