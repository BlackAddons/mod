package org.blackaddons.api.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatChannelTest {

    @Test
    fun `channel prefixes`() {
        assertEquals(ChatChannel.PARTY, ChatChannel.of("Party > [MVP+] Bob: Mimic Dead!"))
        assertEquals(ChatChannel.PARTY, ChatChannel.of("Party Finder > Bob joined the dungeon group! (Archer Level 30)"))
        assertEquals(ChatChannel.GUILD, ChatChannel.of("Guild > [VIP] Bob [Officer]: gm"))
        assertEquals(ChatChannel.OFFICER, ChatChannel.of("Officer > [VIP] Bob [Officer]: gm"))
        assertEquals(ChatChannel.COOP, ChatChannel.of("Co-op > Bob: hi"))
    }

    @Test
    fun `direct messages both ways, but not stash notices`() {
        assertEquals(ChatChannel.PRIVATE, ChatChannel.of("From [MVP+] Bob: hey"))
        assertEquals(ChatChannel.PRIVATE, ChatChannel.of("To Bob: hey"))
        assertNull(ChatChannel.of("From stash: Pufferfish"))
    }

    @Test
    fun `conversation partner with and without a rank`() {
        assertEquals("Bob", ChatChannel.conversation("Opened a chat conversation with [MVP+] Bob for the next 5 minutes. Use /chat a to leave"))
        assertEquals("Bob_2", ChatChannel.conversation("Opened a chat conversation with Bob_2 for the next 5 minutes. Use /chat a to leave"))
        assertNull(ChatChannel.conversation("Bob: Opened a chat conversation with Eve for the next 5 minutes."))
    }

    @Test
    fun `opening a conversation without a message goes to private`() {
        assertEquals(ChatChannel.PRIVATE, ChatChannel.of("Opened a chat conversation with [MVP+] Bob for the next 5 minutes. Use /chat a to leave"))
    }

    @Test
    fun `party notices without a sender go to party`() {
        assertEquals(ChatChannel.PARTY, ChatChannel.of("[MVP+] Bob joined the party."))
        assertEquals(ChatChannel.PARTY, ChatChannel.of("Bob has disbanded the party!"))
        assertEquals(ChatChannel.PARTY, ChatChannel.of("You have joined [MVP+] Bob's party!"))
    }

    @Test
    fun `refusals of a routed message land in the tab it was sent from`() {
        assertEquals(ChatChannel.PARTY, ChatChannel.of("You are not in a party right now."))
        assertEquals(ChatChannel.GUILD, ChatChannel.of("You must be in a guild to use this command!"))
    }

    @Test
    fun `public chat stays public even when it talks about parties`() {
        assertEquals(ChatChannel.ALL, ChatChannel.of("[VIP] Bob: anyone want to join the party?"))
        assertEquals(ChatChannel.ALL, ChatChannel.of("[VIP] Bob: any guild recruiting? I'm not in a guild"))
        assertNull(ChatChannel.of("You are playing on profile: Apple"))
        assertNull(ChatChannel.of("You are playing on profile: Strawberry (Co-op)"))
    }
}
