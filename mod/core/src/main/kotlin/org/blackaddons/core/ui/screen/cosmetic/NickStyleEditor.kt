package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.cosmetics.NickStyle
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.columnWidth
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.cosmetics.me.CosmeticDraft

private val tr = I18n.scope("blackaddons.cosmetic_editor")

/** Toggles for the vanilla styles a player can set. Only admins set obfuscated, so it has no toggle. */
internal class NickStyleEditor(
    private val draft: () -> CosmeticDraft?,
    private val onEdit: () -> Unit,
) : InputTarget {

    private enum class Toggle(val key: String, val fallback: String, val shown: NickStyle) {
        BOLD("style_bold", "Bold", NickStyle(bold = true)),
        ITALIC("style_italic", "Italic", NickStyle(italic = true)),
        UNDERLINED("style_underlined", "Underline", NickStyle(underlined = true)),
        STRIKETHROUGH("style_strikethrough", "Strike", NickStyle(strikethrough = true));

        fun isOn(style: NickStyle): Boolean = when (this) {
            BOLD -> style.bold
            ITALIC -> style.italic
            UNDERLINED -> style.underlined
            STRIKETHROUGH -> style.strikethrough
        }

        fun flip(style: NickStyle): NickStyle = when (this) {
            BOLD -> style.copy(bold = !style.bold)
            ITALIC -> style.copy(italic = !style.italic)
            UNDERLINED -> style.copy(underlined = !style.underlined)
            STRIKETHROUGH -> style.copy(strikethrough = !style.strikethrough)
        }
    }

    private val pills = Array(Toggle.entries.size) { Rect() }

    /** Returns the y below. */
    fun render(x: Float, y: Float, w: Float, d: CosmeticDraft, mouseX: Float, mouseY: Float): Float {
        Render.text(tr("style", "Style"), x, y, TextStyle.SMALL_SECONDARY)
        val top = y + labelAdvance()
        val toggles = Toggle.entries
        val pillW = columnWidth(w, toggles.size, PILL_GAP)
        row(spacing = PILL_GAP, width = w) {
            for ((i, toggle) in toggles.withIndex()) {
                cell(pillW, PILL_H) { cx, cy ->
                    pills[i].set(cx, cy, pillW, PILL_H)
                    drawPill(pills[i], tr(toggle.key, toggle.fallback), toggle.isOn(d.style), mouseX, mouseY, toggle.shown)
                }
            }
        }.draw(x, top)
        return top + PILL_H
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        val d = draft() ?: return false
        if (!mouse.isLeft) return false
        val hit = pills.indexOfFirst { it.has(mouse.x, mouse.y) }
        if (hit < 0) return false
        d.style = Toggle.entries[hit].flip(d.style)
        onEdit()
        return true
    }

    private companion object {
        const val PILL_GAP = 6f
        const val PILL_H = 20f
    }
}
