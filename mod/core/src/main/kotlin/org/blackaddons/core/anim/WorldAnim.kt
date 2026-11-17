package org.blackaddons.core.anim

import kotlin.math.PI

object WorldAnim {

    /** Angle in radians, one turn per [periodMs]. Keeps turning while the game is paused. */
    fun spin(periodMs: Long): Float {
        val phase = (System.currentTimeMillis() % periodMs).toFloat() / periodMs.toFloat()
        return phase * (2f * PI.toFloat())
    }

    /** [speed] 1 means [basePeriodMs]. 0 or less stops it. */
    fun spin(basePeriodMs: Long, speed: Double): Float {
        if (speed <= 0.0) return 0f
        return spin((basePeriodMs / speed).toLong().coerceAtLeast(1L))
    }
}
