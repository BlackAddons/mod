package org.blackaddons.api.gui

/** Shows the single host screen that renders `UiManager`'s top panel and forwards input to it. */
interface ScreenHost {
    /** Any screen (ours, container, chat, pause). Raw input fires regardless of focus, so key handlers check this. */
    val anyScreenOpen: Boolean

    /** Chat frees the cursor but leaves the HUD visible, so HUD elements can be clicked through it. */
    val chatOpen: Boolean get() = false

    fun show()

    fun hide()

    /** Draws the stack over the game's open screen, leaving it in place. */
    fun showOver() = show()

    /** Runs now if on the client thread, otherwise as soon as it's free. */
    fun onClientThread(block: () -> Unit) = block()

    /** Blocking here freezes the game. */
    val isClientThread: Boolean get() = false

    /** SDL builds (26.3+) only deliver typed chars while some [owner] holds text input. */
    fun textInputFocusChanged(owner: Any, focused: Boolean) {}
}
