package org.blackaddons.core.ui.tab.eggs

import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.api.util.graphemes
import java.awt.Color

/** Shared drawing for eggs, which all paint one character at a time. */

/** Returns the width, so callers can walk a name glyph by glyph. */
internal fun glyph(text: String, x: Float, y: Float, color: Color, font: Font = Fonts.medium): Float {
    Render.text(text, x, y, Theme.FONT_BODY, color, font)
    return Render.textWidth(text, Theme.FONT_BODY, font)
}

/** The name at progress 0, or with no egg. */
internal fun plain(name: String, x: Float, y: Float, color: Color): Float {
    var cx = x
    for (g in name.graphemes()) cx += glyph(g, cx, y, color)
    return cx - x
}

/* Keeps the name's own alpha, so a fading row stays faded while its color shifts. */
internal fun lerp(from: Color, to: Color, t: Float): Color = lerpColor(from, to.withAlpha(from.alpha), t)

/** Stable 0..1 per index: distinct from neighbors, not random. */
internal fun hash01(index: Int): Float {
    var h = index * -1640531527
    h = h xor (h ushr 13)
    h *= -1028477387
    h = h xor (h ushr 16)
    return ((h ushr 8) and 0xFFFF) / 65535f
}
