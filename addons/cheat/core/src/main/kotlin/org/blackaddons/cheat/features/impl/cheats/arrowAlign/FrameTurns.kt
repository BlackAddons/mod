package org.blackaddons.cheat.features.impl.cheats.arrowAlign

import org.blackaddons.api.util.ServerClock

/**
 * An arrow's rotation once in-flight turns land. The server reports a round trip late, so going by the frame alone
 * would overshoot (seven extra clicks). Turns stay pending until we see them or their deadline passes, then they count
 * as lost.
 */
internal class FrameTurns(rotation: Int) {

    private var seen = rotation

    private val inFlight = ArrayDeque<ServerClock.Deadline>()

    val predicted: Int get() = (seen + inFlight.size) % ArrowAlignBoard.TURNS

    /** Each eighth moved settles one in-flight turn. */
    fun observe(rotation: Int) {
        val moved = Math.floorMod(rotation - seen, ArrowAlignBoard.TURNS)
        repeat(minOf(moved, inFlight.size)) { inFlight.removeFirst() }
        seen = rotation
    }

    fun sent(deadline: ServerClock.Deadline) {
        inFlight.addLast(deadline)
    }

    fun expire(clock: ServerClock) {
        while (inFlight.firstOrNull()?.let(clock::passed) == true) inFlight.removeFirst()
    }
}
