package org.blackaddons.api.ui.widget

/** Frame counter so stamped click regions can tell if they're from the frame on screen. */
internal object UiFrame {
    var id = 0L
        private set

    /** Only stamps made while drawing are tied to a frame. */
    var drawing = false
        private set

    inline fun <T> drawing(next: Boolean, block: () -> T): T {
        if (next) id++
        drawing = true
        try {
            return block()
        } finally {
            drawing = false
        }
    }
}
