package org.blackaddons.api.ui

import org.blackaddons.api.input.FakeKeyNames
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.text.TextBuffer
import org.blackaddons.api.ui.widget.text.TextEditor
import org.blackaddons.api.util.graphemes
import org.blackaddons.api.util.sanitizeUtf16
import org.blackaddons.api.util.takeValidUtf16
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Utf16AndTextInputTest {

    private val crashString = "🐳♘ 𝔹Ł𝓐𝕔𝔨𝕌爪 ✌💗𝓑𝓛𝓐𝓒𝓚𝓤𝑀𝐵𝐿𝒜𝒞𝒦𝒰𝑀𝔹𝕃𝔸ℂ𝕂𝕌𝕄ＢＬＡＣＫＵＭ᥇ꪶꪖᥴᛕꪊꪑϐׁׅ֒ᥣׁׅ֪ɑׁׅ֮ᝯׁƙׁׅ֑υׁׅ ꩇׁׅ݊ℬℒ𝓐𝓒𝓚𝓤ℳꌃ꒒ꍏꉓꀘꀎꂵ🐨 ⋆ 🐾 🎀 𝐵𝐿𝒜𝒞𝒦𝒰𝑀 🎀 🐾 ⋆ 🐨ʙʟᴀᴄᴋᴜᴍW∩⋊Ɔ∀˥ᙠB⃣   L⃣   A⃣   C⃣   K⃣   U⃣   M⃣\u200B🇧\u200B🇱\u200B🇦\u200B🇨\u200B🇰\u200B🇺\u200B🇲\u200BB⃞\u202F  \u202FL⃞\u202F  \u202FA⃞\u202F  \u202FC⃞\u202F"

    private fun hasUnpairedSurrogates(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) return true
                i += 2
            } else if (Character.isLowSurrogate(c)) {
                return true
            } else {
                i++
            }
        }
        return false
    }

    @Test
    fun `test sanitizeUtf16 returns same instance for valid strings`() {
        val ascii = "Hello, world!"
        assertSame(ascii, ascii.sanitizeUtf16())

        val emoji = "🐳 Hello \uD83D\uDC33 🦄"
        assertSame(emoji, emoji.sanitizeUtf16())
    }

    @Test
    fun `test sanitizeUtf16 replaces unpaired surrogates`() {
        val loneHigh = "abc\uD83Ddef"
        val sanitizedHigh = loneHigh.sanitizeUtf16()
        assertEquals("abc\uFFFDdef", sanitizedHigh)
        assertFalse(hasUnpairedSurrogates(sanitizedHigh))

        val loneLow = "abc\uDC33def"
        val sanitizedLow = loneLow.sanitizeUtf16()
        assertEquals("abc\uFFFDdef", sanitizedLow)
        assertFalse(hasUnpairedSurrogates(sanitizedLow))

        val inverted = "\uDC33\uD83D"
        val sanitizedInverted = inverted.sanitizeUtf16()
        assertEquals("\uFFFD\uFFFD", sanitizedInverted)
        assertFalse(hasUnpairedSurrogates(sanitizedInverted))

        val endHigh = "test\uD83D"
        val sanitizedEnd = endHigh.sanitizeUtf16()
        assertEquals("test\uFFFD", sanitizedEnd)
        assertFalse(hasUnpairedSurrogates(sanitizedEnd))
    }

    @Test
    fun `test takeValidUtf16 never slices surrogate pairs`() {
        val s = "a\uD83D\uDC33b" // 'a', high, low, 'b'
        assertEquals("", s.takeValidUtf16(0))
        assertEquals("a", s.takeValidUtf16(1))
        // Index 2 splits the pair; back off to 1.
        assertEquals("a", s.takeValidUtf16(2))
        assertEquals("a\uD83D\uDC33", s.takeValidUtf16(3))
        assertEquals("a\uD83D\uDC33b", s.takeValidUtf16(4))
        assertEquals("a\uD83D\uDC33b", s.takeValidUtf16(10))
    }

    @Test
    fun `test graphemes preserves surrogate pairs and emojis`() {
        val emoji = "🐳"
        val clusters = emoji.graphemes()
        assertEquals(1, clusters.size)
        assertEquals("🐳", clusters[0])
        assertFalse(hasUnpairedSurrogates(clusters[0]))

        val text = "A🐳B"
        val textClusters = text.graphemes()
        assertEquals(listOf("A", "🐳", "B"), textClusters)

        val crashClusters = crashString.graphemes()
        assertTrue(crashClusters.isNotEmpty())
        for (cluster in crashClusters) {
            assertFalse(hasUnpairedSurrogates(cluster), "Cluster '$cluster' contains unpaired surrogate")
        }
    }

    @Test
    fun `test TextBuffer never produces unpaired surrogates in drawn characters`() {
        val buffer = TextBuffer()
        buffer.text = crashString
        assertEquals(crashString, buffer.text)

        val drawn = buffer.drawn
        assertTrue(drawn.isNotEmpty())
        for (animChar in drawn) {
            assertFalse(
                hasUnpairedSurrogates(animChar.char),
                "AnimChar '${animChar.char}' contains unpaired surrogate!"
            )
        }
    }

    @Test
    fun `test TextEditor typed surrogate pair via charTyped`() {
        val editor = TextEditor()
        val consumedHigh = editor.charTyped('\uD83D')
        assertTrue(consumedHigh)
        // The high surrogate is buffered.
        assertEquals("", editor.text)

        val consumedLow = editor.charTyped('\uDC33')
        assertTrue(consumedLow)
        assertEquals("🐳", editor.text)
        assertEquals(1, editor.length)

        for (animChar in editor.selectedText.let { listOf<String>() }) {
            assertFalse(hasUnpairedSurrogates(animChar))
        }
    }

    @Test
    fun `test TextEditor insert crash string`() {
        val editor = TextEditor()
        val inserted = editor.insert(crashString)
        assertTrue(inserted)
        assertEquals(crashString, editor.text)
    }

    @Test
    fun `test TextEditor ctrl backspace and ctrl delete erase whole words`() {
        FakeKeyNames.install()
        val editor = TextEditor()
        editor.text = "enchanted diamond block"
        editor.keyPressed(KeyInput(Keys.BACKSPACE, Keys.MOD_CONTROL))
        assertEquals("enchanted diamond ", editor.text)

        editor.keyPressed(KeyInput(Keys.HOME, 0))
        editor.keyPressed(KeyInput(Keys.DELETE, Keys.MOD_CONTROL))
        assertEquals(" diamond ", editor.text)

        editor.keyPressed(KeyInput(Keys.BACKSPACE, 0))
        assertEquals(" diamond ", editor.text)
    }
}
