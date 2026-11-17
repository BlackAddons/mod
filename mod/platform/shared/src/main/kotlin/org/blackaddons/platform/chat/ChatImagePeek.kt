package org.blackaddons.platform.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatSpan
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.Hashes
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.chat.ChatImages
import org.blackaddons.core.backend.chat.ChatPicture
import org.blackaddons.core.backend.chat.ChatStore
import org.blackaddons.core.features.impl.settings.BlackaddonsChat
import org.blackaddons.platform.event.impl.RenderScreenEvent

/**
 * Hover a BlackAddons chat line to see its linked picture. `HoverEvent` can't carry images, and drawing inside chat
 * would need a layout mixin fighting every chat mod, so this reuses the Ctrl-click hit test. The address in the line is
 * the only state, carried as copy-to-clipboard so a click never opens a browser.
 */
object ChatImagePeek {

    private val prefix = Backend.CHAT_URL + "/attachments/"

    /**
     * Call once at client init. Screen pass, not HUD: BlackSkija blits HUD-queued draws under screens. Chat is only
     * hoverable with its screen open, so this always runs when needed.
     */
    fun start() {
        EventListener(RenderScreenEvent::class.java, EventPriority.NORMAL) { render() }.register()
    }

    private fun render() {
        if (!BlackaddonsChat.imagePreview) return
        val mouseX = Platform.window.mouseX
        val mouseY = Platform.window.mouseY

        // Null unless the cursor is on open chat. Also guards against other screens.
        val line = Platform.chat.lineAt(mouseX.toDouble(), mouseY.toDouble()) ?: return
        val hash = hashIn(line.message.spans) ?: return
        draw(hash, mouseX, mouseY)
    }

    private fun hashIn(spans: List<ChatSpan>): String? {
        for (span in spans) {
            val url = (span.click as? ChatClick.CopyToClipboard)?.text ?: continue
            if (!url.startsWith(prefix)) continue
            return url.removePrefix(prefix).takeIf(Hashes::isSha256Hex)
        }
        return null
    }

    private fun draw(hash: String, mouseX: Float, mouseY: Float) {
        val picture = ChatStore.picture(hash)
        val natural = if (picture != null && picture.width > 0 && picture.height > 0) {
            picture.width.toFloat() to picture.height.toFloat()
        } else {
            // Unknown size (older line, or no dimensions): a square box.
            MAX_H to MAX_H
        }
        val scale = minOf(1f, MAX_W / natural.first, MAX_H / natural.second)
        val w = natural.first * scale
        val h = natural.second * scale

        val x = (mouseX + CURSOR_GAP).coerceIn(0f, (Platform.window.scaledWidth - w).coerceAtLeast(0f))
        val y = (mouseY - h - CURSOR_GAP).coerceIn(0f, (Platform.window.scaledHeight - h).coerceAtLeast(0f))

        Render.rect(x - PAD, y - PAD, w + PAD * 2f, h + PAD * 2f, Theme.BACKGROUND.withAlpha(220), Theme.RADIUS_SMALL)
        // Older line with no metadata: drawn as a picture, which everything but stickers is.
        val known = picture ?: ChatPicture(hash, 0, 0)
        if (!ChatImages.draw(known, x, y, w, h, Theme.RADIUS_SMALL)) {
            Render.textCentered(
                I18n.tr("blackaddons.bachat.loading"), x + w / 2f,
                Render.centerY(y, h, TextStyle.SMALL_MUTED),
                TextStyle.SMALL_MUTED,
            )
        }
    }

    /** Scaled units. */
    private const val MAX_W = 220f
    private const val MAX_H = 150f

    private const val PAD = 3f
    private const val CURSOR_GAP = 8f
}
