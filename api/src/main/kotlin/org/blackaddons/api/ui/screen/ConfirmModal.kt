package org.blackaddons.api.ui.screen

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.util.I18n

private val tr = I18n.scope("blackaddons.confirm")

/**
 * Confirmation for irreversible actions; confirm is red when [danger]. `UiManager.push(ConfirmModal(...))`.
 * Cancel, Escape and click-away run nothing. Prefer it over a button that turns into its own confirmation.
 */
class ConfirmModal(
    title: String,
    private val message: String = tr("sure", "Are you sure?"),
    private val onConfirm: () -> Unit,
    private val confirmLabel: String = tr("confirm", "Confirm"),
    cancelLabel: String = tr("cancel", "Cancel"),
    private val danger: Boolean = true,
) : ModalPanel(title) {

    private val confirmButton = widgets.add(Button(confirmLabel) {
        UiManager.pop()
        onConfirm()
    }.apply {
        this.danger = this@ConfirmModal.danger
        accent = !this@ConfirmModal.danger
    })

    private val cancelButton = widgets.add(Button(cancelLabel) { UiManager.pop() })

    override val panelWidth: Float get() = WIDTH
    override val panelHeight: Float
        get() {
            val lines = messageLines(WIDTH - Theme.PADDING_LARGE * 2f)
            return Theme.PADDING_LARGE * 2f + Theme.FONT_TITLE + Theme.PADDING + lines.size * lineHeight() +
                Theme.PADDING_LARGE + BUTTON_H
        }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val lineH = lineHeight()
        messageLines(contentW).forEachIndexed { index, line ->
            Render.text(
                line, contentX, contentY + index * lineH,
                TextStyle.BODY_SECONDARY,
            )
        }

        // Confirm rightmost: the last place the pointer reaches.
        val confirmW = maxOf(confirmButton.naturalWidth(), MIN_BUTTON_W)
        val cancelW = maxOf(cancelButton.naturalWidth(), MIN_BUTTON_W)
        val y = contentY + contentH - BUTTON_H
        val confirmX = contentX + contentW - confirmW
        cancelButton.render(contentX, y, cancelW, BUTTON_H, mouseX, mouseY)
        confirmButton.render(confirmX, y, confirmW, BUTTON_H, mouseX, mouseY)
    }

    private fun messageLines(width: Float): List<String> =
        Render.wrapText(message, width, Theme.FONT_BODY, Fonts.regular)

    private fun lineHeight(): Float = Render.textHeight(Theme.FONT_BODY, Fonts.regular)

    private companion object {
        const val WIDTH = 320f
        const val BUTTON_H = 24f

        /** So the pair matches in width. */
        const val MIN_BUTTON_W = 84f
    }
}
