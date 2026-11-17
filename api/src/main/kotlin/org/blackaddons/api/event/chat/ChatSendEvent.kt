package org.blackaddons.api.event.chat

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/** Before chat or a command is sent. Cancel to drop it, or reassign [message]. For commands [message] lacks the `/`. */
class ChatSendEvent(
    var message: String,
    val isCommand: Boolean,
    override var cancelled: Boolean = false,
) : Event(), Cancellable
