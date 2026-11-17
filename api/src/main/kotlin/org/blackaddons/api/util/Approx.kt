package org.blackaddons.api.util

import kotlin.math.abs

/** Good for pixels, blocks, degrees. */
const val FLOAT_TOLERANCE = 1e-4f

const val DOUBLE_TOLERANCE = 1e-9

fun Float.near(other: Float, tolerance: Float = FLOAT_TOLERANCE): Boolean = abs(this - other) <= tolerance

fun Double.near(other: Double, tolerance: Double = DOUBLE_TOLERANCE): Boolean = abs(this - other) <= tolerance

fun Float.nearZero(tolerance: Float = FLOAT_TOLERANCE): Boolean = abs(this) <= tolerance

fun Double.nearZero(tolerance: Double = DOUBLE_TOLERANCE): Boolean = abs(this) <= tolerance
