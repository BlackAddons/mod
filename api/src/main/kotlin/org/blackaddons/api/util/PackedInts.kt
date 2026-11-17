package org.blackaddons.api.util

/** Two ints in one `Long`, a map key without a `Pair`. */
fun packInts(high: Int, low: Int): Long = (high.toLong() shl 32) or (low.toLong() and 0xFFFFFFFFL)

fun Long.packedHigh(): Int = (this shr 32).toInt()

fun Long.packedLow(): Int = toInt()
