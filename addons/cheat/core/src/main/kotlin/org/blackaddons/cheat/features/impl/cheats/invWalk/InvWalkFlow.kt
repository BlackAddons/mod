package org.blackaddons.cheat.features.impl.cheats.invWalk

import org.blackaddons.api.event.world.ClickKind

// Screens InvWalk works in. Another kind is one more entry plus its check in InvWalk.openScreen.
internal enum class WalkScreen(val openForServer: Boolean, val rearms: Boolean) {
    INVENTORY(openForServer = false, rearms = true),
}

internal data class HeldClick(val slotId: Int, val button: Int, val kind: ClickKind)

internal sealed interface WalkStep {
    data object None : WalkStep
    data class Send(val clicks: List<HeldClick>, val chat: Boolean) : WalkStep
    data object Close : WalkStep
}

/*
 * Walk until a click, stand while clicking, walk again once nothing is left on the cursor. A click, command, chat or
 * close only goes out once the server heard the player stand still (no keys, sneak or sprint). Pure, so this order is
 * tested without a game.
 */
internal class InvWalkFlow {
    var screen: WalkScreen? = null
        private set

    var walking = true
        private set

    private var openForServer = false
    private val held = ArrayList<HeldClick>()
    private var chatHeld = false
    private var stopTicks = 0
    private var idleTicks = 0

    fun open(screen: WalkScreen) {
        reset()
        this.screen = screen
        openForServer = screen.openForServer
    }

    fun reset() {
        screen = null
        walking = true
        openForServer = false
        held.clear()
        chatHeld = false
        stopTicks = 0
        idleTicks = 0
    }

    // True when the click may go out now, false when it waits for the player to stand still.
    fun onClick(click: HeldClick, still: Boolean): Boolean {
        if (screen == null) return true
        walking = false
        idleTicks = 0
        if (still && !holding()) {
            openForServer = true
            return true
        }
        startHolding()
        held += click
        return false
    }

    // True when a chat or command packet may go out now. It doesn't open anything for the server.
    fun onChat(still: Boolean): Boolean {
        if (screen == null || still && !holding()) return true
        walking = false
        startHolding()
        chatHeld = true
        return false
    }

    // Once per client tick. itemsClear: nothing on the cursor or in the crafting grid that a close would move.
    fun tick(still: Boolean, itemsClear: Boolean, resumeTicks: Int): WalkStep {
        val screen = screen ?: return WalkStep.None
        if (holding()) {
            if (!still && ++stopTicks < MAX_STOP_TICKS) return WalkStep.None
            val send = WalkStep.Send(held.toList(), chatHeld)
            if (held.isNotEmpty()) openForServer = true
            held.clear()
            chatHeld = false
            return send
        }
        if (walking) return WalkStep.None
        if (!openForServer) {
            walking = true
            return WalkStep.None
        }
        if (!screen.rearms || !still || !itemsClear || ++idleTicks < resumeTicks) return WalkStep.None
        walking = true
        openForServer = false
        return WalkStep.Close
    }

    // The game is about to send a close: keep it back while the server doesn't count the screen open.
    fun keepCloseBack(): Boolean = screen != null && !openForServer

    private fun holding(): Boolean = held.isNotEmpty() || chatHeld

    private fun startHolding() {
        if (!holding()) stopTicks = 0
    }

    companion object {
        const val MAX_STOP_TICKS = 10
    }
}
