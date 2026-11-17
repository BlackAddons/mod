package org.blackaddons.api.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerMessageTest {

    private fun player(line: String) = PlayerMessage.of(line)?.player

    @Test
    fun `public chat with and without a rank`() {
        assertEquals(PlayerMessage("Bob", ChatChannel.ALL, "hi"), PlayerMessage.of("[MVP+] Bob: hi"))
        assertEquals(PlayerMessage("Bob_2", ChatChannel.ALL, "hi"), PlayerMessage.of("Bob_2: hi"))
    }

    @Test
    fun `skyblock level and emblem before the rank`() {
        assertEquals("Bob", player("[287] ♲ [MVP++] Bob: hi"))
        assertEquals("Bob", player("[287] ♲ Bob: hi"))
        assertEquals("Bob", player("[287] [VIP] Bob: hi"))
        assertEquals("Bob", player("[282] ⚓ [MVP+] Bob ●: hi"))
    }

    @Test
    fun `channels and direct messages`() {
        assertEquals(PlayerMessage("Bob", ChatChannel.PARTY, "Mimic Dead!"), PlayerMessage.of("Party > [MVP+] Bob: Mimic Dead!"))
        assertEquals(PlayerMessage("Bob", ChatChannel.GUILD, "gm"), PlayerMessage.of("Guild > [VIP] Bob [Officer]: gm"))
        assertEquals(PlayerMessage("Bob", ChatChannel.OFFICER, "gm"), PlayerMessage.of("Officer > [VIP] Bob [Officer]: gm"))
        assertEquals(PlayerMessage("Bob", ChatChannel.COOP, "hi"), PlayerMessage.of("Co-op > [VIP+] Bob: hi"))
        assertEquals(PlayerMessage("Bob", ChatChannel.PRIVATE, "hey"), PlayerMessage.of("From [MVP+] Bob: hey"))
        assertEquals(PlayerMessage("Bob", ChatChannel.PRIVATE, "hey", outgoing = true), PlayerMessage.of("To Bob: hey"))
    }

    @Test
    fun `conversation notice is not a message`() {
        assertNull(PlayerMessage.of("Opened a chat conversation with [MVP+] Bob for the next 5 minutes. Use /chat a to leave"))
    }

    @Test
    fun `brackets and colons inside the message stay in the text`() {
        assertEquals(PlayerMessage("Bob", ChatChannel.ALL, "look at [this] thing: wow"), PlayerMessage.of("Bob: look at [this] thing: wow"))
        assertEquals(PlayerMessage("Bob", ChatChannel.ALL, "[VIP] Eve: fake"), PlayerMessage.of("[MVP+] Bob: [VIP] Eve: fake"))
    }

    @Test
    fun `system lines and dialogue have no sender`() {
        assertNull(player("Profile ID: 1234"))
        assertNull(player("You are playing on profile: Apple"))
        assertNull(player("[BOSS] Necron: Finally, I heard so much about you."))
        assertNull(player("[NPC] Elle: Hello!"))
        assertNull(player("From stash: Pufferfish"))
        assertNull(player("Bob joined the party."))
        assertNull(player(""))
    }
}
