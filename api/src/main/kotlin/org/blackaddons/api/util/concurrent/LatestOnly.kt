package org.blackaddons.api.util.concurrent
import java.util.concurrent.atomic.AtomicInteger

/** Only the newest restarted [OffThread] lookup publishes. Wrap `onResult` in [next]; [cancel] drops all pending. */
class LatestOnly {

    private val generation = AtomicInteger()

    fun <T> next(publish: (T) -> Unit): (T) -> Unit {
        val token = generation.incrementAndGet()
        return { value -> if (generation.get() == token) publish(value) }
    }

    fun cancel() {
        generation.incrementAndGet()
    }
}
