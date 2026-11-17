package org.blackaddons.api.event.chat

import org.blackaddons.api.chat.ChatLine
import org.blackaddons.api.event.Event

/**
 * [line] was just stored, after all [ChatMessageEvent] listeners. Once per message, client thread; not
 * re-fired for lines kept across a disconnect.
 */
class ChatLineAddedEvent(val line: ChatLine) : Event()
