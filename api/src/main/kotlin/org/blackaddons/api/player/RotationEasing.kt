package org.blackaddons.api.player

import kotlin.math.cos
import kotlin.math.pow

/** Progress shape of a turn. Real flicks ease out; [LINEAR] reads as automated. */
enum class RotationEasing {
    /** Constant rate; the most obvious one. */
    LINEAR,

    /** Gentle deceleration. */
    EASE_OUT_QUAD,

    /** Half sine ease, half exponential ease-out: soft start, long settle. */
    HUMAN;

    /** [t] in 0..1. */
    fun apply(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val inv = 1f - x
        return when (this) {
            LINEAR -> x
            EASE_OUT_QUAD -> 1f - inv * inv
            HUMAN -> (sineInOut(x) + expoOut(x)) * 0.5f
        }
    }

    private fun expoOut(t: Float): Float = if (t >= 1f) 1f else (1.0 - 2.0.pow(-10.0 * t)).toFloat()

    private fun sineInOut(t: Float): Float = (0.5 * (1.0 - cos(Math.PI * t))).toFloat()
}
