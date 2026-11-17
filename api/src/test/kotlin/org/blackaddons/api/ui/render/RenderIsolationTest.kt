package org.blackaddons.api.ui.render

import org.blackaddons.api.testing.FakeRenderer
import org.blackaddons.api.testing.TestPlatform
import org.blackaddons.api.ui.layout.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RenderIsolationTest {

    private val renderer = FakeRenderer()
    private val calls get() = renderer.calls

    @BeforeEach
    fun installRecorder() {
        TestPlatform.renderer = renderer
        Render.beginPass()
        calls.clear()
    }

    @Test
    fun `a throw closes what the block left open, innermost scissor included`() {
        Render.push()
        assertThrows<IllegalStateException> {
            Render.isolated {
                Render.push()
                Render.pushScissor(0, 0, 10, 10)
                Render.pushAntiAlias(false)
                error("boom")
            }
        }

        assertEquals(1, calls.count { it == "popScissor" })
        assertEquals(1, calls.count { it == "pop" })
        assertEquals(1, calls.count { it == "popAntiAlias" })
        assertNull(Render.clip)
        // The caller's earlier push is still the caller's to close.
        Render.pop()
        assertEquals(2, calls.count { it == "pop" })
    }

    @Test
    fun `stamping a hit area inside a layer throws, and the layer ends either way`() {
        assertThrows<IllegalStateException> {
            Render.layer(RenderLayer(), 0, 0, 10, 10) { Rect().set(0f, 0f, 5f, 5f) }
        }
        Rect().set(0f, 0f, 5f, 5f)
    }

    @Test
    fun `a block that returns closes nothing`() {
        Render.isolated { Render.pushScissor(0, 0, 10, 10) }

        assertEquals(0, calls.count { it == "popScissor" })
        Render.popScissor()
    }

    @Test
    fun `a new pass forgets a scissor the last one left open`() {
        Render.pushScissor(0, 0, 10, 10)

        Render.beginPass()

        assertNull(Render.clip)
        assertEquals("beginPass", calls.last())
    }
}
