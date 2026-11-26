package org.blackaddons.api.ui.widget

import org.blackaddons.api.input.FakeKeyNames
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.hit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WidgetHostTest {
    init {
        FakeKeyNames.install()
    }


    // Regions register while drawing, as in the real host.
    private fun frame(block: () -> Unit) = UiFrame.drawing(next = true, block)

    private class Box(val x: Float, val y: Float) : Widget {
        override val width = 10f
        override val height = 10f
        val events = mutableListOf<String>()
        override fun draw(x: Float, y: Float) = Unit
        override fun mouseClicked(mouse: MouseButtonInput): Boolean =
            hit(mouse.x, mouse.y, x, y, width, height).also { if (it) events += "click" }

        override fun mouseDragged(drag: MouseDragInput): Boolean {
            events += "drag"
            return false
        }

        override fun mouseReleased(mouse: MouseButtonInput): Boolean {
            events += "release"
            return false
        }
    }

    @Test
    fun `the widget that took the press alone gets the drag and the release`() {
        val host = WidgetHost()
        val slider = host.add(Box(0f, 0f))
        val other = host.add(Box(20f, 0f))

        assertTrue(host.mouseClicked(MouseButtonInput(5f, 5f, 0)))
        assertTrue(host.mouseDragged(MouseDragInput(25f, 5f, 0, 20f, 0f)), "a captured drag is taken even off the widget")
        assertTrue(host.mouseReleased(MouseButtonInput(25f, 5f, 0)))
        assertEquals(listOf("click", "drag", "release"), slider.events)
        assertTrue(other.events.isEmpty())

        host.mouseDragged(MouseDragInput(5f, 5f, 0, 0f, 0f))
        assertEquals(listOf("drag"), other.events, "with nothing captured a drag is offered to everyone again")
    }

    @Test
    fun `a press that hits nothing drops a capture whose release was lost`() {
        val host = WidgetHost()
        val slider = host.add(Box(0f, 0f))
        host.mouseClicked(MouseButtonInput(5f, 5f, 0))
        host.mouseClicked(MouseButtonInput(100f, 100f, 0))
        host.mouseDragged(MouseDragInput(5f, 5f, 0, 0f, 0f))
        assertEquals(listOf("click", "drag"), slider.events)
        assertFalse(host.mouseDragged(MouseDragInput(5f, 5f, 0, 0f, 0f)), "the drag is offered, not captured")
    }

    @Test
    fun `a region drawn this frame takes its button and reports hover`() {
        val host = WidgetHost()
        var clicks = 0
        var rightClicks = 0
        UiMouse.set(5f, 5f)
        frame {
            assertTrue(host.clickable(0f, 0f, 10f, 10f) { clicks++ })
            host.clickable(0f, 0f, 10f, 10f, Keys.MOUSE_RIGHT) { rightClicks++ }
        }

        assertTrue(host.mouseClicked(MouseButtonInput(5f, 5f, Keys.MOUSE_LEFT)))
        assertTrue(host.mouseClicked(MouseButtonInput(5f, 5f, Keys.MOUSE_RIGHT)))
        assertFalse(host.mouseClicked(MouseButtonInput(15f, 5f, Keys.MOUSE_LEFT)))
        assertEquals(1, clicks)
        assertEquals(1, rightClicks)
    }

    @Test
    fun `the region drawn last wins an overlap`() {
        val host = WidgetHost()
        val order = mutableListOf<String>()
        frame {
            host.clickable(0f, 0f, 20f, 20f) { order += "row" }
            host.clickable(0f, 0f, 5f, 5f) { order += "chevron" }
        }
        host.mouseClicked(MouseButtonInput(2f, 2f, 0))
        host.mouseClicked(MouseButtonInput(10f, 10f, 0))
        assertEquals(listOf("chevron", "row"), order)
    }

    @Test
    fun `a region not drawn in the latest frame cannot be clicked`() {
        val host = WidgetHost()
        var clicks = 0
        frame { host.clickable(0f, 0f, 10f, 10f) { clicks++ } }
        frame {}
        assertFalse(host.mouseClicked(MouseButtonInput(5f, 5f, 0)))
        assertEquals(0, clicks)
    }

    @Test
    fun `a box stamped while a panel draws cannot be clicked once a frame goes by without it`() {
        val box = Rect()
        frame { box.set(0f, 0f, 10f, 10f) }
        assertTrue(box.has(5f, 5f))
        frame {}
        assertFalse(box.has(5f, 5f))

        val hud = Rect().apply { set(0f, 0f, 10f, 10f) }
        frame {}
        assertTrue(hud.has(5f, 5f), "stamped outside the panels, it keeps its place")
    }

    @Test
    fun `a draggable region keeps the pointer until release`() {
        val host = WidgetHost()
        val other = host.add(Box(100f, 100f))
        val trail = mutableListOf<String>()
        frame {
            host.draggable(0f, 0f, 10f, 10f, onDrag = { x, _ -> trail += "drag $x" }, onRelease = { x, _ -> trail += "drop $x" }) { x, _ ->
                trail += "press $x"
            }
        }
        host.mouseClicked(MouseButtonInput(5f, 5f, 0))
        host.mouseDragged(MouseDragInput(105f, 105f, 0, 100f, 100f))
        host.mouseReleased(MouseButtonInput(105f, 105f, 0))
        assertEquals(listOf("press 5.0", "drag 105.0", "drop 105.0"), trail)
        assertTrue(other.events.isEmpty())
    }

    @Test
    fun `a widget beats a region under it`() {
        val host = WidgetHost()
        val button = host.add(Box(0f, 0f))
        var regionClicks = 0
        frame { host.clickable(0f, 0f, 50f, 50f) { regionClicks++ } }
        host.mouseClicked(MouseButtonInput(5f, 5f, 0))
        assertEquals(listOf("click"), button.events)
        assertEquals(0, regionClicks)
    }
}
