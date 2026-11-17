package org.blackaddons.api.event.input

import org.blackaddons.api.event.Event
import org.blackaddons.api.input.Keys

class KeyInputEvent(
    val key: Int,
    val action: Int,
    override val modifiers: Int,
    /** Pressed through [org.blackaddons.api.player.InteractionHost.pressKey], not by the player. */
    val synthetic: Boolean = false,
) : Event(), WithModifiers {
    val isPress: Boolean get() = action == Keys.PRESS
    val isRelease: Boolean get() = action == Keys.RELEASE
    val isRepeat: Boolean get() = action == Keys.REPEAT
}
