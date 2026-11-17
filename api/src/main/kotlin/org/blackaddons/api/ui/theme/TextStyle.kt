package org.blackaddons.api.ui.theme

import org.blackaddons.api.ui.render.Font
import java.awt.Color

/** Size, color slot and font in one. Color is read at draw time so themes repaint. Presets: `SIZE_COLOR[_WEIGHT]`. */
class TextStyle(val size: Float, val slot: ThemeSlot, val font: Font) {

    val color: Color get() = Theme.palette[slot]

    companion object {
        val SMALL_PRIMARY = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_PRIMARY, Fonts.regular)
        val SMALL_PRIMARY_MEDIUM = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_PRIMARY, Fonts.medium)
        val SMALL_PRIMARY_BOLD = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_PRIMARY, Fonts.bold)
        val SMALL_SECONDARY = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_SECONDARY, Fonts.regular)
        val SMALL_SECONDARY_MEDIUM = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_SECONDARY, Fonts.medium)
        val SMALL_SECONDARY_BOLD = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_SECONDARY, Fonts.bold)
        val SMALL_MUTED = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_MUTED, Fonts.regular)
        val SMALL_MUTED_MEDIUM = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_MUTED, Fonts.medium)
        val SMALL_MUTED_BOLD = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_MUTED, Fonts.bold)
        val SMALL_DISABLED = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_DISABLED, Fonts.regular)
        val SMALL_DISABLED_MEDIUM = TextStyle(Theme.FONT_SMALL, ThemeSlot.TEXT_DISABLED, Fonts.medium)
        val BODY_PRIMARY = TextStyle(Theme.FONT_BODY, ThemeSlot.TEXT_PRIMARY, Fonts.regular)
        val BODY_PRIMARY_MEDIUM = TextStyle(Theme.FONT_BODY, ThemeSlot.TEXT_PRIMARY, Fonts.medium)
        val BODY_PRIMARY_BOLD = TextStyle(Theme.FONT_BODY, ThemeSlot.TEXT_PRIMARY, Fonts.bold)
        val BODY_SECONDARY = TextStyle(Theme.FONT_BODY, ThemeSlot.TEXT_SECONDARY, Fonts.regular)
        val BODY_SECONDARY_MEDIUM = TextStyle(Theme.FONT_BODY, ThemeSlot.TEXT_SECONDARY, Fonts.medium)
        val BODY_MUTED = TextStyle(Theme.FONT_BODY, ThemeSlot.TEXT_MUTED, Fonts.regular)
        val BODY_MUTED_MEDIUM = TextStyle(Theme.FONT_BODY, ThemeSlot.TEXT_MUTED, Fonts.medium)
        val TITLE_PRIMARY_MEDIUM = TextStyle(Theme.FONT_TITLE, ThemeSlot.TEXT_PRIMARY, Fonts.medium)
        val TITLE_PRIMARY_BOLD = TextStyle(Theme.FONT_TITLE, ThemeSlot.TEXT_PRIMARY, Fonts.bold)
    }
}
