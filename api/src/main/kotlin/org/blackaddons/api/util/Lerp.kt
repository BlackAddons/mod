package org.blackaddons.api.util

import kotlin.math.exp
import kotlin.math.pow

fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/**
 * The share of the remaining gap an exponential ease at [rate] per second closes in [dt] seconds, the same at any frame
 * rate: `value += (target - value) * expSmoothing(dt, RATE)`.
 */
fun expSmoothing(dt: Double, rate: Double): Double = 1 - exp(-dt * rate)

/**
 * Steps evenly by ratio, not by difference: halfway between 0.3 and 0.03 is about 0.095. Both ends must be positive.
 */
fun logLerp(from: Float, to: Float, t: Float): Float = from * (to / from).pow(t)
