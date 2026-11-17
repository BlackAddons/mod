package org.blackaddons.cheat.aim

import kotlin.random.Random

/**
 * The on-contact click of one aim. It's ready after a random 0..[maxDelay] ticks on target, so it's not always on the
 * first tick.
 */
internal class ContactClick(maxDelay: Int, random: Random = Random) {
    private val delay = random.nextInt(maxDelay.coerceAtLeast(0) + 1)
    private var ticks = 0

    /** Call this once per tick. */
    fun ready(onTarget: Boolean): Boolean {
        if (onTarget) ticks++
        return onTarget && ticks > delay
    }
}
