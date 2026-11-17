package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.layout.LayoutNode

/**
 * Anything that takes input, from a control to a whole screen part. Register with a [WidgetHost]. Every
 * method defaults to not consumed.
 */
interface InputTarget {
    /** Absolute coords; true if consumed. */
    fun mouseClicked(mouse: MouseButtonInput): Boolean = false

    fun mouseReleased(mouse: MouseButtonInput): Boolean = false

    fun mouseDragged(drag: MouseDragInput): Boolean = false

    fun mouseScrolled(wheel: MouseScrollInput): Boolean = false

    /** Only while [focused]. */
    fun keyPressed(key: KeyInput): Boolean = false

    /** Only while [focused]. */
    fun charTyped(typed: CharInput): Boolean = false

    /**
     * Drawn over its neighbors (open list, suggestions); a [WidgetHost] offers it presses and scrolls first.
     */
    val isOverlaying: Boolean get() = false

    /** Set by [WidgetHost]. Stateless controls keep the no-op default. */
    var focused: Boolean
        get() = false
        set(value) {}
}

/** An interactive [LayoutNode] hit-testing where it last drew, so it works inside `column`/`row` via `+widget`. */
interface Widget : LayoutNode, InputTarget
