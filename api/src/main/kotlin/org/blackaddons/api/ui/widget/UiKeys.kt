package org.blackaddons.api.ui.widget

import org.blackaddons.api.input.Keys

/** Modifiers held right now, for callbacks like scroll that don't carry them. */
object UiKeys {

    val ctrl: Boolean get() = down(Keys.LEFT_CONTROL) || down(Keys.RIGHT_CONTROL)

    val shift: Boolean get() = down(Keys.LEFT_SHIFT) || down(Keys.RIGHT_SHIFT)

    val alt: Boolean get() = down(Keys.LEFT_ALT) || down(Keys.RIGHT_ALT)

    private fun down(key: Int): Boolean = Keys.isDown(key)
}
