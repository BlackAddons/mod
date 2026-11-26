package org.blackaddons.api.util.io

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

/** Players paste portable strings, so each failure must say what's wrong ("a theme, not a HUD"), not just "invalid". */
class PortableTest {

    private val document = JsonParser.parseString(
        """{"seeds":{"surface":"#1c1e26"},"overrides":{},"name":"Midnight"}"""
    ).asJsonObject

    @Test
    fun `a document survives the round trip`() {
        val read = Portable.decode(Portable.encode("theme", document))

        val decoded = assertInstanceOf(PortableRead.Document::class.java, read)
        assertEquals("theme", decoded.kind)
        assertEquals(document, decoded.json)
    }

    @Test
    fun `the kind comes back so a caller can refuse one it did not ask for`() {
        val read = Portable.decode(Portable.encode("hud", document))

        assertEquals("hud", assertInstanceOf(PortableRead.Document::class.java, read).kind)
    }

    @Test
    fun `whitespace anywhere is forgiven`() {
        val wrapped = Portable.encode("theme", document).chunked(20).joinToString("\n")

        assertInstanceOf(PortableRead.Document::class.java, Portable.decode("  $wrapped  "))
    }

    @Test
    fun `nothing, something else, and a newer format are three different answers`() {
        assertEquals(PortableRead.Empty, Portable.decode("   "))
        assertEquals(PortableRead.NotPortable, Portable.decode("https://example.com/a.b"))
        assertEquals(PortableRead.NotPortable, Portable.decode("hello"))
        assertEquals(PortableRead.NewerFormat(9), Portable.decode("BLACKADDONS9.theme.AAAA"))
    }

    @Test
    fun `a truncated payload reads as corrupt rather than as someone else's string`() {
        val encoded = Portable.encode("theme", document)
        val cut = encoded.substring(0, encoded.length - 12)

        assertEquals(PortableRead.Corrupt, Portable.decode(cut))
    }

    @Test
    fun `a payload that inflates out of all proportion is refused`() {
        // 4 MB of one character deflates to a few hundred bytes: cheap to paste, expensive to read.
        val bomb = Portable.encode("theme", JsonObject().apply { addProperty("a", "x".repeat(4 * 1024 * 1024)) })

        assertEquals(PortableRead.TooLarge, Portable.decode(bomb))
    }

    @Test
    fun `well formed base64 that is not deflated data is corrupt`() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(64) { it.toByte() })

        assertEquals(PortableRead.Corrupt, Portable.decode("BLACKADDONS1.theme.$payload"))
    }

    @Test
    fun `encoding a theme stays short enough to paste`() {
        assertTrue(Portable.encode("theme", document).length < 300, "a theme has to fit in a message")
    }
}
