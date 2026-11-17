package org.blackaddons.api.chat

import org.blackaddons.api.Platform

/** A Hypixel chat channel. [command] sends a line into it: `/pc hi`. */
enum class ChatChannel(val command: String?) {
    ALL("ac"),
    PARTY("pc"),
    GUILD("gc"),
    OFFICER("oc"),
    COOP("cc"),
    PRIVATE(null),
    ;

    companion object {

        private val PREFIXES = listOf(
            "Party > " to PARTY,
            "Party Finder > " to PARTY,
            "Guild > " to GUILD,
            "Officer > " to OFFICER,
            "Co-op > " to COOP,
        )

        // Server notices that belong to a channel, including the refusals of a line sent to it.
        private val NOTICES = listOf(
            PARTY to listOf(
                "joined the party", "left the party", "disbanded the party", "the party was disbanded",
                "to the party", "from the party", "'s party", "party leader", "party members",
                "party moderators", "party warp", "transferred the party", "in a party",
            ),
            GUILD to listOf("in a guild", "the guild", "your guild"),
            PRIVATE to listOf("opened a chat conversation with"),
        )

        private val CONVERSATION = Regex("""^Opened a chat conversation with (?:\[[^\]]+] )?(\w{1,16}) for the next """)

        /** The channel of an unformatted line: a player's message or a server notice about it. Null for the rest. */
        fun of(line: String): ChatChannel? {
            PREFIXES.firstOrNull { (prefix, _) -> line.startsWith(prefix) }?.let { return it.second }
            PlayerMessage.of(line)?.let { return it.channel }
            val lower = line.lowercase()
            return NOTICES.firstOrNull { (_, phrases) -> phrases.any { it in lower } }?.first
        }

        /** Who `/chat <name>` opened a conversation with, from its notice. */
        fun conversation(line: String): String? = CONVERSATION.find(line)?.groupValues?.get(1)
    }
}

/**
 * A line a player wrote, read off Hypixel's format: `Party > [MVP+] Bob: hi`, `From [VIP] Bob: hey`. Ranks,
 * Skyblock levels and emblems are skipped; NPC and boss dialogue is not a player message.
 */
data class PlayerMessage(
    /** The name in the line: who wrote it, or who received a private message you sent ([outgoing]). */
    val player: String,
    val channel: ChatChannel,
    val text: String,
    /** A private message you sent (`To Bob: ...`). */
    val outgoing: Boolean = false,
) {

    /** You wrote it. */
    val isOwn: Boolean get() = outgoing || player.equals(Platform.player.name, ignoreCase = true)

    companion object {

        private val LINE = Regex(
            """^(?:(Party|Guild|Officer|Co-op) > |(From|To) )?(.*?\] )??(?:[^\w\s] )?(\w{1,16})(?: \[[^\]]+])?(?: [^\w\s])?: (.*)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        private val NOT_PLAYERS = listOf("[NPC]", "[BOSS]")

        private const val STASH = "From stash: "

        /** Parses an unformatted line. Null when no player wrote it. */
        fun of(line: String): PlayerMessage? {
            if (line.startsWith(STASH)) return null
            val match = LINE.find(line) ?: return null
            val (channel, direction, prefix, name, text) = match.destructured
            if (NOT_PLAYERS.any { it in prefix }) return null
            return PlayerMessage(
                player = name,
                channel = when (channel) {
                    "Party" -> ChatChannel.PARTY
                    "Guild" -> ChatChannel.GUILD
                    "Officer" -> ChatChannel.OFFICER
                    "Co-op" -> ChatChannel.COOP
                    else -> if (direction.isEmpty()) ChatChannel.ALL else ChatChannel.PRIVATE
                },
                text = text,
                outgoing = direction == "To",
            )
        }
    }
}
