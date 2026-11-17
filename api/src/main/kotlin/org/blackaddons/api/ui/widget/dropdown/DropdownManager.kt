package org.blackaddons.api.ui.widget.dropdown
/** A popup that takes part in [DropdownManager]'s one-open-at-a-time rule. */
interface PopupControl {
    var open: Boolean
    fun closeImmediately()
}

/** Keeps at most one popup open across the whole UI. */
object DropdownManager {
    private var active: PopupControl? = null

    fun onOpen(popup: PopupControl) {
        if (active != null && active !== popup) {
            active?.closeImmediately()
        }
        active = popup
    }

    fun onClose(popup: PopupControl) {
        if (active === popup) {
            active = null
        }
    }

    fun closeAll() {
        val current = active
        active = null
        current?.closeImmediately()
    }
}
