package org.blackaddons.api.ui.widget

import org.blackaddons.api.input.Keys

/**
 * A mouse button going down or up at [x], [y] in panel coordinates. [button] is `Keys.MOUSE_LEFT`, `MOUSE_RIGHT` or
 * `MOUSE_MIDDLE`; [isLeft] and its siblings read better than comparing it.
 */
class MouseButtonInput(val x: Float, val y: Float, val button: Int) {
    val isLeft: Boolean get() = button == Keys.MOUSE_LEFT
    val isRight: Boolean get() = button == Keys.MOUSE_RIGHT
    val isMiddle: Boolean get() = button == Keys.MOUSE_MIDDLE

    /** The same press at another point, for a child laid out in its own coordinates. */
    fun at(x: Float, y: Float) = MouseButtonInput(x, y, button)
}

/** The pointer moved [dx], [dy] since the last drag with [button] held, now at [x], [y]. */
class MouseDragInput(val x: Float, val y: Float, val button: Int, val dx: Float, val dy: Float) {
    val isLeft: Boolean get() = button == Keys.MOUSE_LEFT
    val isRight: Boolean get() = button == Keys.MOUSE_RIGHT

    fun at(x: Float, y: Float) = MouseDragInput(x, y, button, dx, dy)
}

/** The wheel turned [amount] notches over [x], [y]: positive away from the player (up). */
class MouseScrollInput(val x: Float, val y: Float, val amount: Float) {
    fun at(x: Float, y: Float) = MouseScrollInput(x, y, amount)
}

/** A key press (or its repeat) with the modifiers held. [code] is a `Keys` code. */
class KeyInput(val code: Int, val modifiers: Int) {
    val shift: Boolean get() = (modifiers and Keys.MOD_SHIFT) != 0
    val ctrl: Boolean get() = (modifiers and Keys.MOD_CONTROL) != 0
    val alt: Boolean get() = (modifiers and Keys.MOD_ALT) != 0
}

/** A character typed, after the keyboard layout and dead keys did their work. */
class CharInput(val char: Char, val modifiers: Int)
