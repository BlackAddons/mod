package org.blackaddons.api.event.chat

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/**
 * Every message reaching `ChatHud.addMessage`. [type] is `PLAYER`, `SYSTEM` or null for local calls; filters
 * should only cancel when `!isSystem`.
 */
class ChatMessageEvent(
    val raw: String,
    val unformatted: String,
    val type: ChatMessageType? = null,
    override var cancelled: Boolean = false,
) : Event(), Cancellable {

    val isSystem: Boolean get() = type == ChatMessageType.SYSTEM

    /** Appended to what's stored and shown; [raw] stays as sent. */
    var suffix: ChatMessage? = null
}

enum class ChatMessageType { SYSTEM, PLAYER }
