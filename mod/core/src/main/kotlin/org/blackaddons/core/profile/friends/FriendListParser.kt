package org.blackaddons.core.profile.friends

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.chat.ChatSpan
import org.blackaddons.api.util.stripFormatting

/**
 * Account names from Hypixel friend chat. Pure, for tests. Formats checked against SkyblockAPI's `FriendsAPI` (MIT,
 * github.com/SkyblockAPI/SkyblockAPI).
 */
internal object FriendListParser {

    /** A whole page is one message. This is its header. */
    private val PAGE_HEADER = Regex("""Friends \(Page (\d+) of (\d+)\)""")

    /** `Notch is currently offline`. `*` marks a nick. */
    private val ENTRY = Regex("""^(\S+?)(\*)? is """)

    /** A nicked friend's real name is only in the hover. */
    private val PROFILE_HOVER = Regex("""Click here to view ([A-Za-z0-9_]+)'s profile""")

    private val ACCOUNT_NAME = Regex("""\w{3,16}""")

    /*
     * Hypixel (1.8) puts `§` codes in the text content, so patterns have to match past them. That's why
     * [stripFormatting] exists.
     */
    private val ADDED = Regex("""^You are now friends with (?:\[.+] )?(\w+)""")
    private val REMOVED = Regex("""^You removed (?:\[.+] )?(\w+) from your friends list!""")
    private val JOIN_LEAVE = Regex("""^Friend > (?:\[.+] )?(\w+) (?:joined|left)""")

    /** Run it before building spans, because `ChatReceiveEvent` builds spans lazily to save work. */
    fun looksLikePage(unformatted: String): Boolean = PAGE_HEADER.containsMatchIn(unformatted)

    fun namesOnPage(message: ChatMessage): List<String> {
        val lines = message.lines()
        val header = lines.indexOfFirst { PAGE_HEADER.containsMatchIn(it.text()) }
        if (header < 0) return emptyList()
        return lines.drop(header + 1).mapNotNull(::nameOn)
    }

    fun nameAdded(unformatted: String): String? =
        (ADDED.find(unformatted) ?: JOIN_LEAVE.find(unformatted))?.groupValues?.get(1)

    fun nameRemoved(unformatted: String): String? = REMOVED.find(unformatted)?.groupValues?.get(1)

    private fun nameOn(line: List<ChatSpan>): String? {
        val entry = ENTRY.find(line.text()) ?: return null
        // The hover always names the account, so use it first. Without it, a nick would get stored as a name.
        line.profileHover()?.let { return it }
        if (entry.groupValues[2].isNotEmpty()) return null
        return entry.groupValues[1].takeIf { ACCOUNT_NAME.matches(it) }
    }

    private fun List<ChatSpan>.profileHover(): String? = firstNotNullOfOrNull { span ->
        span.hover?.let { PROFILE_HOVER.find(it.stripFormatting())?.groupValues?.get(1) }
    }

    private fun List<ChatSpan>.text() = joinToString("") { it.text }.stripFormatting()

    private fun ChatMessage.lines(): List<List<ChatSpan>> {
        val lines = ArrayList<List<ChatSpan>>()
        var current = ArrayList<ChatSpan>()
        for (span in spans) {
            val parts = span.text.split('\n')
            parts.forEachIndexed { i, part ->
                if (i > 0) {
                    lines += current
                    current = ArrayList()
                }
                if (part.isNotEmpty()) current += span.copy(text = part)
            }
        }
        lines += current
        return lines
    }
}
