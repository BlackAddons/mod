package org.blackaddons.api.chat

import java.awt.Color

/** A stored chat message; identity is the line, not the text. Client thread only. */
interface ChatLine {

    /** Server text with formatting codes, before client decoration. */
    val raw: String

    val unformatted: String

    /** What the chat shows, decorations included. */
    val message: ChatMessage

    var highlight: Color?

    /** Who said what, when a player wrote the line. */
    val playerMessage: PlayerMessage? get() = PlayerMessage.of(unformatted)

    /** The channel a player message or a server notice belongs to. */
    val channel: ChatChannel? get() = ChatChannel.of(unformatted)

    /** No-op once the chat has forgotten the line. */
    fun remove()
}
