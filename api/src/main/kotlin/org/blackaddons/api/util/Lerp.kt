package org.blackaddons.api.util

import kotlin.math.pow

fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/**
 * Steps evenly by ratio, not by difference: halfway between 0.3 and 0.03 is about 0.095. Both ends must be positive.
 */
fun logLerp(from: Float, to: Float, t: Float): Float = from * (to / from).pow(t)
