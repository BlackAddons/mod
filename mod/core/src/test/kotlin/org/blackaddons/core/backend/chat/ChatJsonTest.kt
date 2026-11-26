package org.blackaddons.core.backend.chat

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The wire shape of the backend's chat line: camelCase, fields omitted instead of null, source spelled as an enum. Pins
 * the defensive reads: empty lines get refused, and timestamps are UTC with or without a zone (otherwise history
 * reorders).
 */
class ChatJsonTest {

    private fun parse(json: String) = ChatJson.line(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `a full line is read whole`() {
        val line = parse(
            """
            {
              "id": 42,
              "channel": "global",
              "userId": "8b9f6f1c-0d4a-4a52-9b0e-2f4b6d5a1c33",
              "playerUuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
              "senderName": "Notch",
              "source": "Mod",
              "content": "hello",
              "createdAt": "2026-08-27T12:04:05.123Z",
              "replyTo": { "id": 41, "senderName": "Steve", "excerpt": "anyone there",
                           "playerUuid": "ec561538-f3fd-461d-aff5-086b22154bce" },
              "attachments": [
                { "hash": "${"a".repeat(64)}", "contentType": "image/png", "size": 10, "width": 16, "height": 9, "frames": 1 }
              ]
            }
            """,
        )

        assertNotNull(line)
        assertEquals(42L, line!!.id)
        assertEquals(ChatChannel.GLOBAL, line.channel)
        assertEquals("Notch", line.senderName)
        assertEquals(ChatSource.MOD, line.source)
        assertEquals("hello", line.content)
        assertEquals(Instant.parse("2026-08-27T12:04:05.123Z").toEpochMilli(), line.createdAt)
        assertEquals(41L, line.quote?.id)
        assertEquals("anyone there", line.quote?.excerpt)
        assertEquals("ec561538-f3fd-461d-aff5-086b22154bce", line.quote?.playerUuid?.toString())
        assertEquals(16, line.pictures.single().width)
    }

    @Test
    fun `an unedited line says so by omitting the field`() {
        val line = parse("""{ "id": 1, "channel": "global", "senderName": "Steve", "content": "hi" }""")
        assertNotNull(line)
        assertFalse(line!!.edited)
        assertNull(line.editedAt)
    }

    @Test
    fun `only a changed text sets the edited stamp`() {
        val line = parse(
            """{ "id": 1, "channel": "global", "senderName": "Steve", "content": "hi",
                 "editedAt": "2026-08-27T12:05:00Z" }""",
        )
        assertTrue(line!!.edited)
    }

    @Test
    fun `a naive timestamp is read as UTC rather than dropped`() {
        val naive = parse(
            """{ "id": 1, "channel": "global", "senderName": "Steve", "content": "hi",
                 "createdAt": "2026-08-27T12:04:05.123" }""",
        )
        val zoned = parse(
            """{ "id": 2, "channel": "global", "senderName": "Steve", "content": "hi",
                 "createdAt": "2026-08-27T12:04:05.123Z" }""",
        )
        assertEquals(zoned!!.createdAt, naive!!.createdAt)
    }

    @Test
    fun `a line with neither text nor a picture is not a line`() {
        assertNull(parse("""{ "id": 1, "channel": "global", "senderName": "Steve", "content": "   " }"""))
    }

    @Test
    fun `a picture on its own is a line`() {
        val line = parse(
            """{ "id": 1, "channel": "global", "senderName": "Steve", "content": "",
                 "attachments": [ { "hash": "${"b".repeat(64)}", "width": 4, "height": 4 } ] }""",
        )
        assertNotNull(line)
        assertEquals(1, line!!.pictures.size)
    }

    @Test
    fun `a handle that is not a sha256 is dropped, because it goes straight into a URL`() {
        val line = parse(
            """{ "id": 1, "channel": "global", "senderName": "Steve", "content": "look",
                 "attachments": [ { "hash": "../../etc/passwd" }, { "hash": "${"C".repeat(64)}" } ] }""",
        )
        assertTrue(line!!.pictures.isEmpty()) { "uppercase is not how the pool spells a hash either" }
    }

    @Test
    fun `a channel this build has never heard of is refused`() {
        assertNull(parse("""{ "id": 1, "channel": "traders", "senderName": "Steve", "content": "hi" }"""))
    }

    @Test
    fun `a link is folded out of the drawn text when it became the picture beside it`() {
        val line = parse(
            """{ "id": 1, "channel": "global", "senderName": "Steve", "content": "look at this https://tenor.com/x.gif",
                 "attachments": [ { "hash": "${"d".repeat(64)}", "width": 4, "height": 4 } ] }""",
        )
        assertEquals("look at this", line!!.shown)
        assertEquals(listOf("https://tenor.com/x.gif"), line.links) {
            "folded, not lost - the window puts it back on request"
        }
        assertEquals("look at this https://tenor.com/x.gif", line.content) {
            "what was said is what gets copied"
        }
    }

    @Test
    fun `a link on a line with no picture is left alone`() {
        val line = parse(
            """{ "id": 1, "channel": "global", "senderName": "Steve", "content": "read https://example.com" }""",
        )
        assertEquals("read https://example.com", line!!.shown)
        assertTrue(line.links.isEmpty()) { "nothing to fold without a picture saying the same thing" }
    }

    @Test
    fun `a Discord avatar on Discord's CDN is kept`() {
        val url = "https://cdn.discordapp.com/avatars/1/abc.png?size=64"
        val line = parse(
            """{ "id": 1, "channel": "global", "senderName": "Steve", "content": "hi", "source": "Discord",
                 "avatarUrl": "$url" }""",
        )
        assertEquals(url, line!!.discordAvatar)
    }

    @Test
    fun `an avatar anywhere else is dropped, because the window fetches it`() {
        listOf(
            "http://cdn.discordapp.com/avatars/1/abc.png",
            "https://cdn.discordapp.com.evil.example/a.png",
            "https://user@cdn.discordapp.com/a.png",
            "https://cdn.discordapp.com:8443/a.png",
            "not a url at all",
        ).forEach { url ->
            val line = parse("""{ "id": 1, "channel": "global", "senderName": "Steve", "content": "hi", "avatarUrl": "$url" }""")
            assertNull(line!!.discordAvatar) { url }
        }
    }

    @Test
    fun `an unknown source reads as the mod rather than failing the line`() {
        val line = parse(
            """{ "id": 1, "channel": "global", "senderName": "Steve", "content": "hi", "source": "Telegram" }""",
        )
        assertEquals(ChatSource.MOD, line!!.source)
    }
}
