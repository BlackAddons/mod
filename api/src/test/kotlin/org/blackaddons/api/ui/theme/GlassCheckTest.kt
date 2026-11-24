package org.blackaddons.api.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlassCheckTest {

    @Test
    fun `an unchecked box shows no accent and a checked one no frost or edge`() {
        assertEquals(0, GlassCheck.accent(0f) ushr 24)
        assertEquals(0, GlassCheck.glow(0f) ushr 24)
        assertTrue(GlassCheck.edge(0f, 0f) ushr 24 > 0)
        assertEquals(Theme.ACCENT.rgb, GlassCheck.accent(1f))
        assertEquals(0, GlassCheck.frost(1f, 1f) ushr 24)
        assertEquals(0, GlassCheck.edge(1f, 1f) ushr 24)
    }

    @Test
    fun `hover turns the edge to the accent`() {
        assertEquals(Theme.ACCENT.rgb, GlassCheck.edge(0f, 1f))
    }
}
