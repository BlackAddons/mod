package org.blackaddons.api.event.chat

import org.blackaddons.api.chat.ChatChannel
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.chat.PlayerMessage
import org.blackaddons.api.event.Event

/**
 * Every chat message straight off the packet, so it fires even if another mod hides the line. Not cancellable
 * - suppress or rewrite with [ChatMessageEvent]. [type] is `SYSTEM` for nearly all of Hypixel; the action bar
 * goes to [ActionBarReceiveEvent].
 */
class ChatReceiveEvent(
    val raw: String,
    val unformatted: String,
    val type: ChatMessageType,
    structured: () -> ChatMessage,
) : Event() {

    /**
     * Styled spans with click/hover data (e.g. the hover names the account behind a nick). Built lazily since
     * most listeners only read [unformatted].
     */
    val message: ChatMessage by lazy(structured)

    /** Who said what, when a player wrote the line. */
    val playerMessage: PlayerMessage? by lazy { PlayerMessage.of(unformatted) }

    /** The channel a player message or a server notice belongs to. */
    val channel: ChatChannel? by lazy { ChatChannel.of(unformatted) }

    val isSystem: Boolean get() = type == ChatMessageType.SYSTEM
}
