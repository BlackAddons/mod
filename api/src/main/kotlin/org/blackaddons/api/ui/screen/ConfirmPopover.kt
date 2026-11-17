package org.blackaddons.api.ui.screen

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.util.I18n

private val tr = I18n.scope("blackaddons.confirm")

/**
 * Lighter [ConfirmModal] pinned next to its control, no dim. Anchor is the control's box in screen pixels;
 * the card goes below, or above near the bottom edge.
 */
class ConfirmPopover(
    private val anchorX: Float = UiMouse.x,
    private val anchorY: Float = UiMouse.y,
    private val anchorW: Float = 0f,
    private val anchorH: Float = 0f,
    private val message: String = tr("sure", "Are you sure?"),
    confirmLabel: String = tr("confirm", "Confirm"),
    cancelLabel: String = tr("cancel", "Cancel"),
    private val danger: Boolean = true,
    private val onConfirm: () -> Unit,
) : Popover() {

    private val confirmButton = widgets.add(Button(confirmLabel) {
        UiManager.pop()
        onConfirm()
    }.apply {
        this.danger = this@ConfirmPopover.danger
        accent = !this@ConfirmPopover.danger
    })

    private val cancelButton = widgets.add(Button(cancelLabel) { UiManager.pop() })

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        // Prompt width is capped so it wraps; buttons aren't, so labels never clip.
        val textW = Render.textWidth(message, Theme.FONT_SMALL, Fonts.regular).coerceAtMost(MAX_TEXT_W)
        val buttonsW = maxOf(cancelButton.naturalWidth(), confirmButton.naturalWidth()) * 2f + Theme.PADDING_SMALL
        val contentW = maxOf(textW, buttonsW)
        val lines = Render.wrapText(message, contentW, Theme.FONT_SMALL, Fonts.regular)
        val lineH = Render.textHeight(Theme.FONT_SMALL, Fonts.regular)
        val textH = lines.size * lineH
        val w = contentW + Theme.PADDING * 2f
        val h = Theme.PADDING * 2f + textH + Theme.PADDING + BUTTON_H
        placeUnder(anchorX, anchorY, anchorW, anchorH, w, h, GAP)

        // Click-away dismisses, never confirms.
        drawCard(rise = RISE) {
            val x = card.x
            val y = card.y
            lines.forEachIndexed { index, line ->
                Render.text(
                    line, x + Theme.PADDING, y + Theme.PADDING + index * lineH,
                    TextStyle.SMALL_PRIMARY,
                )
            }

            val half = (contentW - Theme.PADDING_SMALL) / 2f
            val buttonY = y + h - Theme.PADDING - BUTTON_H
            cancelButton.render(x + Theme.PADDING, buttonY, half, BUTTON_H, mouseX, mouseY)
            confirmButton.render(x + w - Theme.PADDING - half, buttonY, half, BUTTON_H, mouseX, mouseY)
        }
    }

    private companion object {
        /** Longer prompts wrap. */
        const val MAX_TEXT_W = 150f
        const val BUTTON_H = 20f
        const val GAP = 4f
        const val RISE = 4f
    }
}
