package org.blackaddons.core.profile.friends

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.chat.ChatSpan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * From a real `/f list` capture (2026-07-30). The first parser used third-party regexes and failed. Hypixel puts legacy
 * codes in content (`§b21Argentius12 §eis in SkyBlock`), so patterns have to look past codes in the middle of a phrase.
 */
class FriendListParserTest {

    /** As captured: one message, spans split at newlines, hovers as sent. */
    private fun capturedPage() = ChatMessage(
        listOf(
            ChatSpan("-----------------------------------------------------"),
            ChatSpan("\n"),
            ChatSpan("                          "),
            ChatSpan(" §6Friends (Page 1 of 9) "),
            ChatSpan(">>", hover = "Click to view page 2"),
            ChatSpan("\n"),
            entry("§b21Argentius12 §eis in SkyBlock - Crystal Hollows", "§b21Argentius12"),
            ChatSpan("\n"),
            entry("§6FER_O §eis in SkyBlock - Crimson Isle", "§6FER_O"),
            ChatSpan("\n"),
            entry("§bW0OP §eis in SkyBlock - Master Mode The Catacombs - §4[MM] §eFloor V", "§bW0OP"),
            ChatSpan("\n"),
            entry("§b663ms §cis currently offline", "§b663ms"),
            ChatSpan("\n"),
            ChatSpan("-----------------------------------------------------"),
        ),
    )

    private fun entry(text: String, hoverName: String) = ChatSpan(
        text,
        hover = "Friends for 7 months\n\n§eClick here to view $hoverName§e's profile",
    )

    @Test
    fun `reads every name off a real captured page`() {
        assertEquals(
            listOf("21Argentius12", "FER_O", "W0OP", "663ms"),
            FriendListParser.namesOnPage(capturedPage()),
        )
    }

    @Test
    fun `the frame lines and the page arrow are not names`() {
        val names = FriendListParser.namesOnPage(capturedPage())
        assertFalse(names.any { it.startsWith("-") })
        assertFalse(names.contains(">>"))
    }

    @Test
    fun `resolves a nick through the hover, not the visible text`() {
        val message = ChatMessage(
            listOf(
                ChatSpan(" §6Friends (Page 2 of 9) "),
                ChatSpan("\n"),
                entry("§bCoolNick123* §eis in SkyBlock - Hub", "§bDinnerbone"),
            ),
        )
        assertEquals(listOf("Dinnerbone"), FriendListParser.namesOnPage(message))
    }

    @Test
    fun `drops a nick that has no hover to resolve it`() {
        val message = ChatMessage(
            listOf(
                ChatSpan(" §6Friends (Page 1 of 1) "),
                ChatSpan("\n"),
                ChatSpan("§bCoolNick123* §eis in SkyBlock - Hub"),
            ),
        )
        assertTrue(FriendListParser.namesOnPage(message).isEmpty())
    }

    @Test
    fun `ignores a message that is not a friends page`() {
        // Lobby-join lines also have a profile hover, but they aren't friend entries.
        val joined = ChatMessage(
            listOf(
                ChatSpan(
                    "§b[MVP§2+§b] Articstocrat§f §6joined the lobby!",
                    hover = "§eClick to view §bArticstocrat§e's profile!",
                ),
            ),
        )
        assertTrue(FriendListParser.namesOnPage(joined).isEmpty())
        assertFalse(FriendListParser.looksLikePage("[MVP+] Articstocrat joined the lobby!"))
    }

    @Test
    fun `the page hint matches the real header and nothing else`() {
        assertTrue(FriendListParser.looksLikePage("                           Friends (Page 1 of 9) >>"))
        assertTrue(FriendListParser.looksLikePage(" << Friends (Page 2 of 9) >>"))
        assertFalse(FriendListParser.looksLikePage("Friends of mine"))
    }

    @Test
    fun `reads the one-line add, join and leave notices`() {
        assertEquals("Notch", FriendListParser.nameAdded("You are now friends with Notch"))
        assertEquals("Notch", FriendListParser.nameAdded("You are now friends with [MVP+] Notch"))
        assertEquals("Notch", FriendListParser.nameAdded("Friend > Notch joined."))
        assertEquals("Notch", FriendListParser.nameAdded("Friend > [MVP+] Notch left."))
        assertNull(FriendListParser.nameAdded("21Argentius12 is in SkyBlock - Crystal Hollows"))
    }

    @Test
    fun `reads the removal notice, and only that`() {
        assertEquals("Notch", FriendListParser.nameRemoved("You removed Notch from your friends list!"))
        assertEquals("Notch", FriendListParser.nameRemoved("You removed [MVP+] Notch from your friends list!"))
        assertNull(FriendListParser.nameRemoved("You are now friends with Notch"))
        assertNull(FriendListParser.nameAdded("You removed Notch from your friends list!"))
    }
}
