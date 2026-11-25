package org.blackaddons.api.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlassDecorTest {

    private val w = 12
    private val h = 10
    private val dark = 0xFF101010.toInt()
    private val light = 0xFFE0E0E0.toInt()

    // Vanilla's look: a black outline, a panel, an 18-style slot frame and a separate arrow mark.
    private fun texture(): IntArray {
        val px = IntArray(w * h) { PANEL }
        for (x in 0 until w) {
            px[x] = BLACK
            px[(h - 1) * w + x] = BLACK
        }
        for (y in 0 until h) {
            px[y * w] = BLACK
            px[y * w + w - 1] = BLACK
        }
        for (y in 2..5) for (x in 2..5) px[y * w + x] = if (x == 2 || y == 2) SHADOW else SLOT
        return px
    }

    @Test
    fun `panel, bevel and slot frames lift to nothing`() {
        assertNull(GlassDecor.lift(texture(), w, h, intArrayOf(3, 3), dark, light))
    }

    @Test
    fun `marks take the theme by brightness and colored pixels stay`() {
        val px = texture()
        px[5 * w + 8] = SLOT
        px[2 * w + 9] = WHITE
        px[7 * w + 9] = RED
        val out = checkNotNull(GlassDecor.lift(px, w, h, intArrayOf(3, 3), dark, light)) { "expected marks" }
        assertEquals(dark and 0xFFFFFF, out[5 * w + 8] and 0xFFFFFF)
        assertTrue(out[5 * w + 8] ushr 24 in 1..254, "a mid-gray mark is partly transparent")
        assertEquals(light, out[2 * w + 9])
        assertEquals(RED, out[7 * w + 9])
        assertEquals(0, out[3 * w + 3], "the slot is cleared")
        assertEquals(0, out[0], "the bevel is cleared")
        assertEquals(0, out[8 * w + 6], "the panel is cleared")
    }

    @Test
    fun `an unseeded slot frame stays as a mark`() {
        val out = checkNotNull(GlassDecor.lift(texture(), w, h, IntArray(0), dark, light)) { "expected marks" }
        assertTrue(out[3 * w + 3] ushr 24 > 0)
    }

    @Test
    fun `an icon touching the bevel keeps its outline`() {
        val px = texture()
        for (y in 1 until h - 1) px[y * w + w - 2] = SHADOW
        px[6 * w + w - 3] = BROWN
        px[6 * w + w - 4] = GREEN
        val out = checkNotNull(GlassDecor.lift(px, w, h, intArrayOf(3, 3), dark, light)) { "expected marks" }
        assertEquals(0, out[3 * w + w - 2], "the bevel is cleared")
        assertTrue(out[6 * w + w - 3] ushr 24 > 0, "the outline stays")
        assertEquals(GREEN, out[6 * w + w - 4])
    }

    private companion object {
        const val PANEL = 0xFFC6C6C6.toInt()
        const val SLOT = 0xFF8B8B8B.toInt()
        const val SHADOW = 0xFF373737.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFFF2020.toInt()
        const val GREEN = 0xFF56AD56.toInt()
        const val BROWN = 0xFF161005.toInt()
    }
}
