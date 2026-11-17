package org.blackaddons.api.gui

/** GUI-scaled window size. Changes with the window - read it each frame. */
interface WindowMetrics {
    val scaledWidth: Float

    val scaledHeight: Float

    /** Minecraft's GUI Scale, exposed so a screen can undo it. */
    val guiScale: Float

    /** Only meaningful while a screen frees the cursor. */
    val mouseX: Float get() = 0f

    val mouseY: Float get() = 0f
}
