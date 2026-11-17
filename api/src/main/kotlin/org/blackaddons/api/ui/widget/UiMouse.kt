package org.blackaddons.api.ui.widget

import org.blackaddons.api.HostApi

/** Pointer position set per frame by [org.blackaddons.api.ui.screen.UiManager], so laid-out widgets resolve hover. */
object UiMouse {
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    @HostApi
    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }
}
