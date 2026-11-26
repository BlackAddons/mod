package org.blackaddons.api.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private val MAX_FRAME = 100.milliseconds

/**
 * Seconds since the previous [tick]. The first tick returns 0, and no tick returns more than [cap], so a frame after a
 * pause continues instead of jumping ahead.
 */
class FrameClock(
    private val cap: Duration = MAX_FRAME,
    private val time: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private var last: Duration? = null
    private val origin = time.markNow()

    fun tick(): Double {
        val now = origin.elapsedNow()
        val dt = last?.let { (now - it).coerceAtMost(cap) } ?: Duration.ZERO
        last = now
        return dt.toDouble(DurationUnit.SECONDS)
    }

    fun reset() {
        last = null
    }
}
