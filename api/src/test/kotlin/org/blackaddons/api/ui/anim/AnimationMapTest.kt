package org.blackaddons.api.ui.anim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimationMapTest {

    @Test
    fun `a settled map starts a new key at its state`() {
        val states = AnimationMap<String>(10_000, settled = true)
        assertEquals(1f, states["on", true])
        assertEquals(0f, states["off", false])
    }

    @Test
    fun `a settled key eases on change and is kept`() {
        val states = AnimationMap<String>(10_000, settled = true)
        states["box", false]
        assertTrue(states["box", true] < 1f)
        assertEquals(1, states.size)
        states.clear()
        assertEquals(0, states.size)
    }
}
