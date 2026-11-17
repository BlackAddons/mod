package org.blackaddons.core.ui.tab.eggs

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.GradientColors
import org.blackaddons.api.util.graphemes
import java.awt.Color

/**
 * BLACKUM opens a gap after `BLACK` and a `C` fades into it, so no letter ever sits where there's no room. The split is
 * found in the name, not hardcoded to 5, in case the nick changes.
 */
internal object BlackumEgg {

    fun draw(name: String, x: Float, y: Float, progress: Float, color: Color, brand: List<ColorStop>): Float {
        val split = name.indexOf(SPLIT_AFTER, ignoreCase = true).takeIf { it >= 0 }?.plus(SPLIT_AFTER.length)
            ?: return plain(name, x, y, color)

        val head = name.take(split)
        val tail = name.drop(split)
        val headGraphemes = head.graphemes()
        val tailGraphemes = tail.graphemes()
        val totalGraphemes = headGraphemes.size + tailGraphemes.size
        val ramp = GradientColors.perChar(totalGraphemes, brand)
        val insertW = Render.textWidth(" $INSERT", Theme.FONT_BODY, Fonts.medium)

        var cx = x
        for ((i, glyph) in headGraphemes.withIndex()) {
            cx += glyph(glyph, cx, y, lerp(color, Color(ramp[i]), progress))
        }

        // The letter stays centered in the opening gap.
        val gap = insertW * progress
        val inserted = Color(ramp.getOrElse(headGraphemes.size) { ramp.last() }).withAlpha((255 * progress).toInt())
        glyph(INSERT, cx + gap - Render.textWidth(INSERT, Theme.FONT_BODY, Fonts.medium), y, inserted)

        cx += gap
        for ((i, glyph) in tailGraphemes.withIndex()) {
            cx += glyph(glyph, cx, y, lerp(color, Color(ramp[headGraphemes.size + i]), progress))
        }
        return cx - x
    }

    private const val SPLIT_AFTER = "BLACK"
    private const val INSERT = "C"
}
