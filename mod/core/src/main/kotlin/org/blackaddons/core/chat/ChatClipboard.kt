package org.blackaddons.core.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatLine
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n

internal object ChatClipboard {

    fun copy(line: ChatLine, keepCodes: Boolean): Boolean {
        val text = if (keepCodes) line.raw else line.unformatted
        if (text.isBlank()) return false

        Platform.clipboard.copy(text)
        Toasts.show(
            I18n.trOr(
                if (keepCodes) "blackaddons.chat_copy.copied_raw" else "blackaddons.chat_copy.copied",
                if (keepCodes) "Message copied (with formatting)" else "Message copied",
            ),
            // Plain text, because formatting codes make it unreadable.
            detail = preview(line.unformatted),
            kind = ToastKind.SUCCESS,
        )
        return true
    }

    private fun preview(text: String): String {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return ""
        return if (line.length <= PREVIEW_CHARS) line else line.take(PREVIEW_CHARS - 3).trimEnd() + "..."
    }

    private const val PREVIEW_CHARS = 48
}
