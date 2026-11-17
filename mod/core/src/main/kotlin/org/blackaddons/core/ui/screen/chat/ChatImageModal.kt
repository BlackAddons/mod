package org.blackaddons.core.ui.screen.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.chat.ChatImages
import org.blackaddons.core.backend.chat.ChatPicture

/** Full-size picture over the chat. Saving goes through the browser, so the file ends up in the player's downloads. */
internal class ChatImageModal(private val picture: ChatPicture, caption: String) : ModalPanel(caption) {

    private val url = Backend.CHAT_URL + "/attachments/" + picture.hash

    private val openButton = widgets.add(Button(I18n.tr("blackaddons.bachat.open_browser")) {
        Platform.desktop.openUrl(url)
    }.apply { accent = true })

    private val copyButton = widgets.add(Button(I18n.tr("blackaddons.bachat.copy_link")) {
        Platform.clipboard.copy(url)
        Toasts.show(I18n.tr("blackaddons.bachat.link_copied"), kind = ToastKind.SUCCESS)
    })

    override val panelWidth: Float
        get() = maxOf(fittedWidth(), MIN_CONTENT_W) + Theme.PADDING_LARGE * 2f

    override val panelHeight: Float
        get() = Theme.PADDING_LARGE * 2f + Theme.FONT_TITLE + Theme.PADDING +
            fittedHeight() + Theme.PADDING + BUTTON_H

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val width = fittedWidth()
        val height = fittedHeight()

        val x = centered(contentX, contentW, width)
        if (!ChatImages.draw(picture, x, contentY, width, height, Theme.RADIUS)) {
            Render.rect(x, contentY, width, height, Theme.SURFACE.withAlpha(120), Theme.RADIUS)
            Render.textCentered(
                I18n.tr("blackaddons.bachat.loading"), x + width / 2f,
                Render.centerY(contentY, height, TextStyle.BODY_MUTED),
                TextStyle.BODY_MUTED,
            )
        }

        val buttonsY = contentY + height + Theme.PADDING
        val openW = openButton.naturalWidth().coerceAtLeast(MIN_BUTTON_W)
        val copyW = copyButton.naturalWidth().coerceAtLeast(MIN_BUTTON_W)
        openButton.render(contentX, buttonsY, openW, BUTTON_H, mouseX, mouseY)
        copyButton.render(contentX + openW + Theme.PADDING, buttonsY, copyW, BUTTON_H, mouseX, mouseY)
    }

    /** From the message dimensions, so the window opens at its final size. */
    private fun scale(): Float {
        val maxW = UiManager.screenWidth * SCREEN_FRACTION - Theme.PADDING_LARGE * 2f
        val maxH = UiManager.screenHeight * SCREEN_FRACTION -
            (Theme.PADDING_LARGE * 2f + Theme.FONT_TITLE + Theme.PADDING * 2f + BUTTON_H)
        return minOf(1f, maxW / picture.boxWidth(FALLBACK_SIDE), maxH / picture.boxHeight(FALLBACK_SIDE))
    }

    private fun fittedWidth(): Float = picture.boxWidth(FALLBACK_SIDE) * scale()

    private fun fittedHeight(): Float = picture.boxHeight(FALLBACK_SIDE) * scale()

    companion object {
        private const val SCREEN_FRACTION = 0.85f
        private const val BUTTON_H = 20f
        private const val MIN_BUTTON_W = 90f
        private const val MIN_CONTENT_W = 220f
        private const val FALLBACK_SIDE = 400f

        fun open(picture: ChatPicture, caption: String) {
            UiManager.push(ChatImageModal(picture, caption))
        }
    }
}
