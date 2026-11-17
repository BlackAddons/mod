package org.blackaddons.api.ui.widget

import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render

/**
 * Dispatches input to [InputTarget]s and owns their single keyboard focus. Every panel has one as `widgets`.
 * Clicks go to overlaying targets, then widgets front to back, then this frame's [clickable] regions;
 * the target taking a press gets focus and captures drags and the release. Keys go to [focusedTarget].
 * Hosts nest: a tab can register its own host in the screen's.
 */
class WidgetHost : InputTarget {

    private val widgets = ArrayList<InputTarget>()
    private val regions = ArrayList<Region>()
    private var regionsFrame = -1L
    private var captured: InputTarget? = null

    var focusedTarget: InputTarget? = null
        private set

    // As a nested target it's focused while anything inside is; losing focus clears the inner one.
    override var focused: Boolean
        get() = focusedTarget != null
        set(value) {
            if (!value) focus(null)
        }

    override val isOverlaying: Boolean get() = widgets.any { it.isOverlaying }

    /** Returns [widget]: `val done = widgets.add(Button(...))`. */
    fun <T : InputTarget> add(widget: T): T {
        widgets += widget
        return widget
    }

    fun remove(widget: InputTarget) {
        widgets -= widget
        if (focusedTarget === widget) focus(null)
        if (captured === widget) captured = null
    }

    /** Call before re-registering a rebuilt set. */
    fun clear() {
        widgets.clear()
        focusedTarget = null
        captured = null
    }

    fun focus(widget: InputTarget?) {
        if (focusedTarget === widget) return
        focusedTarget?.focused = false
        focusedTarget = widget
        widget?.focused = true
    }

    /**
     * Makes a non-widget box clickable for this frame and returns hover: `val hovered =
     * widgets.clickable(...) { open(row) }`. Later regions win overlaps; regions are cut to their scissor.
     */
    fun clickable(
        x: Float, y: Float, w: Float, h: Float,
        button: Int = Keys.MOUSE_LEFT,
        onClick: () -> Unit,
    ): Boolean = draggable(x, y, w, h, button, onPress = { _, _ -> onClick() })

    /** A [clickable] that captures the pointer: drags go to [onDrag] and the release to [onRelease]. */
    fun draggable(
        x: Float, y: Float, w: Float, h: Float,
        button: Int = Keys.MOUSE_LEFT,
        onDrag: (mouseX: Float, mouseY: Float) -> Unit = { _, _ -> },
        onRelease: (mouseX: Float, mouseY: Float) -> Unit = { _, _ -> },
        onPress: (mouseX: Float, mouseY: Float) -> Unit,
    ): Boolean {
        if (regionsFrame != UiFrame.id) {
            regions.clear()
            regionsFrame = UiFrame.id
        }
        val clip = Render.clip
        val x1 = if (clip == null) x else maxOf(x, clip[0])
        val y1 = if (clip == null) y else maxOf(y, clip[1])
        val x2 = if (clip == null) x + w else minOf(x + w, clip[2])
        val y2 = if (clip == null) y + h else minOf(y + h, clip[3])
        val region = Region(x1, y1, x2 - x1, y2 - y1, button, onPress, onDrag, onRelease)
        regions += region
        return hit(UiMouse.x, UiMouse.y, region.x, region.y, region.w, region.h)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        // A release lost outside the window must not leave a stale capture.
        captured = null
        // Overlaying targets first; a press off an open list closes it before continuing.
        val popup = overlaying()
        if (popup != null && take(popup, mouse)) return true
        for (w in widgets.indices.reversed()) {
            val widget = widgets[w]
            if (widget !== popup && take(widget, mouse)) return true
        }
        focus(null)
        return clickRegion(mouse)
    }

    private fun take(target: InputTarget, mouse: MouseButtonInput): Boolean {
        if (!target.mouseClicked(mouse)) return false
        focus(target)
        captured = target
        return true
    }

    private fun overlaying(): InputTarget? = widgets.lastOrNull { it.isOverlaying }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        val owner = captured ?: return anyConsumes { it.mouseReleased(mouse) }
        captured = null
        owner.mouseReleased(mouse)
        return true
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        val owner = captured ?: return anyConsumes { it.mouseDragged(drag) }
        owner.mouseDragged(drag)
        return true
    }

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (overlaying()?.mouseScrolled(wheel) == true) return true
        return anyConsumes { it.mouseScrolled(wheel) }
    }

    override fun keyPressed(key: KeyInput): Boolean =
        focusedTarget?.keyPressed(key) ?: false

    override fun charTyped(typed: CharInput): Boolean =
        focusedTarget?.charTyped(typed) ?: false

    // Regions from an older frame belong to something no longer shown.
    private fun clickRegion(mouse: MouseButtonInput): Boolean {
        if (regionsFrame != UiFrame.id) return false
        val region = regions.lastOrNull { it.button == mouse.button && hit(mouse.x, mouse.y, it.x, it.y, it.w, it.h) }
            ?: return false
        region.onPress(mouse.x, mouse.y)
        captured = region
        return true
    }

    private inline fun anyConsumes(event: (InputTarget) -> Boolean): Boolean {
        for (w in widgets.indices.reversed()) if (event(widgets[w])) return true
        return false
    }

    // Captured like a widget once pressed.
    private class Region(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val button: Int,
        val onPress: (Float, Float) -> Unit,
        private val onDrag: (Float, Float) -> Unit,
        private val onRelease: (Float, Float) -> Unit,
    ) : InputTarget {
        override fun mouseDragged(drag: MouseDragInput): Boolean {
            onDrag(drag.x, drag.y)
            return true
        }

        override fun mouseReleased(mouse: MouseButtonInput): Boolean {
            onRelease(mouse.x, mouse.y)
            return true
        }
    }
}
