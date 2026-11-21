package org.blackaddons.api.event.chat

import org.blackaddons.api.event.Event

/**
 * A chat or command packet is about to leave, from any path: unlike [ChatSendEvent] this sees our own
 * [org.blackaddons.api.chat.ChatHost.sendCommand] too. For commands [message] lacks the `/`. Set [held] to keep the
 * packet back until [org.blackaddons.api.chat.ChatHost.releaseHeld].
 */
class ChatPacketEvent(val message: String, val isCommand: Boolean) : Event() {
    var held = false
}
