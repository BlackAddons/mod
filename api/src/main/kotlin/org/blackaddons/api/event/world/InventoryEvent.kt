package org.blackaddons.api.event.world

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

sealed class InventoryEvent : Event() {

    /**
     * A slot click in any container. [slotId] uses vanilla numbering; [button] is vanilla's argument (0/1 for
     * pickup, hotbar slot for a swap). Cancelling sends no packet.
     */
    class SlotClick(
        val slotId: Int,
        val button: Int,
        val kind: ClickKind,
        override var cancelled: Boolean = false,
    ) : InventoryEvent(), Cancellable

    /** The client is about to tell the server the open menu closed. Cancelling keeps the packet back, the screen still closes. */
    class Close(override var cancelled: Boolean = false) : InventoryEvent(), Cancellable
}

enum class ClickKind {
    /** Left/right click. */
    PICKUP,

    /** Shift-click. */
    QUICK_MOVE,

    /** Number-key hotbar swap. */
    SWAP,

    /** Middle-click. */
    CLONE,

    /** Q / Ctrl-Q. */
    THROW,

    /** Drag across slots. */
    QUICK_CRAFT,

    /** Double-click collect. */
    PICKUP_ALL,

    UNKNOWN,
}
