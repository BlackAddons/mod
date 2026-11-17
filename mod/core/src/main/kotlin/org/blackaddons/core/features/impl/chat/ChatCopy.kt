package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.input.Keys
import org.blackaddons.core.chat.ChatClipboard

/** Ctrl-click copies a whole chat message (wrapped lines depend on chat width). Shift keeps formatting codes. */
object ChatCopy : Feature(
    description = "Ctrl-click a chat message to copy it (add Shift to keep formatting codes).",
    name = "Chat Copy",
) {

    override fun init() {
        register<MouseClickEvent> {
            if (!event.isPress || event.button != Keys.MOUSE_LEFT || !event.ctrl) return@register
            if (copyUnderCursor(keepCodes = event.shift)) {
                // Vanilla would do bs on click - link or closing the chat.
                event.cancelled = true
            }
        }
    }

    private fun copyUnderCursor(keepCodes: Boolean): Boolean {
        val line = Platform.chat.lineAt(Platform.window.mouseX.toDouble(), Platform.window.mouseY.toDouble())
            ?: return false
        return ChatClipboard.copy(line, keepCodes)
    }
}
