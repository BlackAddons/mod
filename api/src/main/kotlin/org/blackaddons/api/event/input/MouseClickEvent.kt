package org.blackaddons.api.event.input

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event
import org.blackaddons.api.input.Keys

class MouseClickEvent(
    val x: Double,
    val y: Double,
    val button: Int,
    val action: Int,
    override val modifiers: Int = 0,
    override var cancelled: Boolean = false,
) : Event(), Cancellable, WithModifiers {
    val isPress: Boolean get() = action == Keys.PRESS
    val isRelease: Boolean get() = action == Keys.RELEASE
}
