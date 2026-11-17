package org.blackaddons.api.event.chat

import org.blackaddons.api.event.Event
import java.util.UUID

/**
 * A new line in BlackAddons' global chat, the one shown in game with the IRC tab. Read-only, and never a
 * [ChatReceiveEvent]: anyone can write here, so nothing that reacts to the server's chat should see it. History
 * loaded on scroll does not post. Client thread.
 */
class IrcMessageEvent(
    val id: Long,
    /** Who wrote it. A Discord author has no Minecraft account, so [playerUuid] is null for them. */
    val sender: String,
    val playerUuid: UUID?,
    val source: IrcSource,
    val text: String,
    /** The line it answers, if any. */
    val quote: IrcQuote?,
    val sentAtMs: Long,
) : Event()

/** Where an IRC line was written. */
enum class IrcSource { GAME, WEBSITE, DISCORD, SYSTEM }

data class IrcQuote(val id: Long, val sender: String, val excerpt: String)
