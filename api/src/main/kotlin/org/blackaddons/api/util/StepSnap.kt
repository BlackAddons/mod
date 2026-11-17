package org.blackaddons.api.util

import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToLong

/**
 * Clamps and snaps to [step] (<= 0 = free). Shared by `NumberSetting` and `Slider` so thumb and label agree.
 */
internal fun Double.snapToStep(min: Double, max: Double, step: Double): Double {
    val clamped = coerceIn(min, max)
    return if (step > 0) (round(clamped / step) * step).coerceIn(min, max) else clamped
}

/** Via a power of ten, avoiding `0.29000000000000004`. */
fun Double.roundToDecimals(decimals: Int): Double {
    if (!isFinite()) return this
    val scale = 10.0.pow(decimals)
    return (this * scale).roundToLong() / scale
}
