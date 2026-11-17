package org.blackaddons.core.backend.gateway

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Exponential backoff with jitter, so clients that dropped together don't all reconnect at the same time. Atomic,
 * because three threads request delays.
 */
internal class GatewayBackoff {

    private val attempt = AtomicInteger()

    fun nextDelaySeconds(): Long {
        val rung = attempt.getAndIncrement()
        val ceiling = min(BASE_SECONDS shl min(rung, MAX_SHIFT), MAX_SECONDS)
        val floor = ceiling / 2
        return floor + ThreadLocalRandom.current().nextLong(ceiling - floor + 1)
    }

    fun withinWindowSeconds(window: Int): Long {
        val bounded = window.coerceIn(1, MAX_SECONDS.toInt()).toLong()
        return 1 + ThreadLocalRandom.current().nextLong(bounded)
    }

    fun reset() {
        attempt.set(0)
    }

    private companion object {
        const val BASE_SECONDS = 2L
        const val MAX_SECONDS = 300L
        const val MAX_SHIFT = 8
    }
}
