package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.cosmetics.PlayerCosmetics
import org.blackaddons.core.cosmetics.me.CosmeticDraft
import org.blackaddons.core.cosmetics.me.MeCosmeticService

private val tr = I18n.scope("blackaddons.cosmetic_editor")

/**
 * The name's font. Like the display name, it's not part of the draft: picking one puts it on right away through its own
 * endpoint, because the backend has to ask Fontsource what the name needs.
 */
internal class NickFontEditor(
    private val draft: () -> CosmeticDraft?,
    private val openPopover: (Panel) -> Unit,
) : InputTarget {

    private val choose = Rect()
    private val reset = Rect()

    @Volatile private var asking = false
    @Volatile private var result: MeCosmeticService.FontChange? = null

    /** Returns the y below. */
    fun render(x: Float, y: Float, w: Float, d: CosmeticDraft, mouseX: Float, mouseY: Float): Float {
        // Only read once. From here on an applied font belongs to the draft, and only a refusal stays here to be shown.
        (result as? MeCosmeticService.FontChange.Applied)?.let { applied ->
            result = null
            d.font = applied.font
            PlayerCosmetics.ownFontSaved(applied.font)
        }

        Render.text(tr("font", "Font: %s").format(d.font?.family ?: tr("font_game", "Game font")), x, y, TextStyle.SMALL_SECONDARY)
        val top = y + labelAdvance()
        val pillW = (w - PILL_GAP) / 2f
        choose.set(x, top, pillW, PILL_H)
        reset.set(x + pillW + PILL_GAP, top, pillW, PILL_H)
        drawPill(choose, if (asking) tr("font_asking", "Putting on...") else tr("font_choose", "Choose..."), false, mouseX, mouseY)
        drawPill(reset, tr("font_game", "Game font"), d.font == null, mouseX, mouseY)

        val refused = result as? MeCosmeticService.FontChange.Refused ?: return top + PILL_H
        val statusY = top + PILL_H + 3f
        val message = refused.message ?: tr("font_refused", "The font was not changed")
        Render.text(message, x, statusY, Theme.FONT_SMALL, Theme.DANGER, Fonts.regular, maxWidth = w)
        return statusY + labelAdvance()
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        val d = draft() ?: return false
        if (!mouse.isLeft || asking) return false
        val mx = mouse.x
        val my = mouse.y
        return when {
            choose.has(mx, my) -> {
                openPopover(NickFontPicker { entry -> ask { MeCosmeticService.setFont(entry.id) } })
                true
            }
            reset.has(mx, my) && d.font != null -> {
                ask { MeCosmeticService.clearFont() }
                true
            }
            else -> false
        }
    }

    private fun ask(change: () -> MeCosmeticService.FontChange) {
        asking = true
        result = null
        OffThread.run(
            name = "cosmetic-font",
            work = change,
            onFailure = { MeCosmeticService.FontChange.Refused(null) },
            onResult = {
                result = it
                asking = false
            },
        )
    }

    private companion object {
        const val PILL_GAP = 6f
        const val PILL_H = 20f
    }
}
