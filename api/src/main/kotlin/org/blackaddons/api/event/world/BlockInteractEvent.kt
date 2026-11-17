package org.blackaddons.api.event.world

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/** Local player used or attacked a block. Fired before anything is sent, so cancelling blocks it entirely. */
sealed class BlockInteractEvent(
    val x: Int,
    val y: Int,
    val z: Int,
) : Event(), Cancellable {
    override var cancelled: Boolean = false

    /** [synthetic] is true for clicks sent by [org.blackaddons.api.player.InteractionHost]. */
    class Use(x: Int, y: Int, z: Int, val synthetic: Boolean = false) : BlockInteractEvent(x, y, z)

    class Attack(x: Int, y: Int, z: Int) : BlockInteractEvent(x, y, z)

    /**
     * Held attack is about to keep breaking the block, every tick. Cancelling also stops the break in progress,
     * because vanilla drops use clicks while one runs.
     */
    class Mine(x: Int, y: Int, z: Int) : BlockInteractEvent(x, y, z)
}
