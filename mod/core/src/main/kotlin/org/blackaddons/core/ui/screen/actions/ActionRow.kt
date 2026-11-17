package org.blackaddons.core.ui.screen.actions

import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hovered
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.ScrollableList
import java.awt.Color

internal object ActionRow {

    const val GRIP_X = 2f
    const val TITLE_X = GRIP_X + ScrollableList.GRIP_W

    private const val PILL_H = 16f
    private const val PILL_PAD = 8f
    private const val PILL_GAP = 4f
    private const val PILL_MIN_TEXT = 24f
    private const val TAG_PAD = 6f
    private const val TAG_FILL_ALPHA = 35

    fun background(rx: Float, ry: Float, rw: Float, rowH: Float, enabled: Boolean, resting: Color? = null) {
        val hovered = hovered(rx, ry, rw, rowH)
        val fill = when {
            hovered -> Theme.SURFACE_HOVER
            resting != null -> resting
            enabled -> Theme.SURFACE
            else -> Theme.SURFACE.withAlpha(120)
        }
        Render.rect(rx, ry, rw, rowH, fill, Theme.RADIUS_SMALL)
    }

    /** Small tag. Returns the next x. Blank [text] draws nothing. Tags past [limit] get an ellipsis or get skipped. */
    fun pill(text: String?, startX: Float, ry: Float, rowH: Float, fill: Color, limit: Float): Float {
        if (text.isNullOrBlank()) return startX
        val room = limit - startX - PILL_PAD
        if (room < PILL_MIN_TEXT) return startX
        val w = Render.textWidth(Render.ellipsize(text, room, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, Fonts.medium) + PILL_PAD
        val y = centered(ry, rowH, PILL_H)
        Render.rect(startX, y, w, PILL_H, fill, 4f)
        Render.textCentered(
            text, startX + w / 2f,
            Render.centerY(y, PILL_H, TextStyle.SMALL_PRIMARY_MEDIUM),
            TextStyle.SMALL_PRIMARY_MEDIUM,
            maxWidth = room,
        )
        return startX + w + PILL_GAP
    }

    fun tag(text: String, x: Float, y: Float, h: Float, color: Color) {
        Render.rect(x, y, tagWidth(text), h, color.withAlpha(TAG_FILL_ALPHA), Theme.RADIUS_SMALL)
        Render.text(text, x + TAG_PAD, Render.centerY(y, h, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, color, Fonts.medium)
    }

    fun tagWidth(text: String): Float = Render.textWidth(text, Theme.FONT_SMALL, Fonts.medium) + TAG_PAD * 2f

    fun title(text: String, x: Float, ry: Float, rowH: Float, maxWidth: Float, enabled: Boolean, font: Font = Fonts.medium): Float {
        val color = if (enabled) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED
        val w = Render.text(
            text, x, Render.centerY(ry, rowH, Theme.FONT_BODY, font), Theme.FONT_BODY, color, font,
            maxWidth = maxWidth.coerceAtLeast(40f),
        )
        return x + w + 10f
    }
}
