package org.blackaddons.api.util

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** Client-tick countdown driven by the owner's tick listener, so it can't outlive the feature. Unarmed is [ready]. */
class TickTimer {

    private var remaining = 0

    val ready: Boolean get() = remaining <= 0

    fun tick() {
        if (remaining > 0) remaining--
    }

    /** Plus up to [jitter] ticks so waits aren't constant. */
    fun arm(ticks: Int, jitter: Int = 0) {
        val spread = if (jitter > 0) ThreadLocalRandom.current().nextInt(jitter + 1) else 0
        remaining = (ticks + spread).coerceAtLeast(0)
    }

    /** Uniform in [minMs]..[maxMs], rounded to ticks. */
    fun armMillis(minMs: Double, maxMs: Double) {
        val lo = minMs.coerceAtMost(maxMs)
        val ms = if (lo == maxMs) lo else ThreadLocalRandom.current().nextDouble(lo, maxMs)
        remaining = (ms / MS_PER_TICK).roundToInt().coerceAtLeast(0)
    }

    /** A uniformly random time in [delay], rounded to ticks. */
    fun arm(delay: ClosedRange<Duration>) =
        armMillis(delay.start.toDouble(DurationUnit.MILLISECONDS), delay.endInclusive.toDouble(DurationUnit.MILLISECONDS))

    /** Next [ready] is true. */
    fun clear() {
        remaining = 0
    }
}
