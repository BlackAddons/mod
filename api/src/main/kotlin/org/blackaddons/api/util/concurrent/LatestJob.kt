package org.blackaddons.api.util.concurrent

import org.blackaddons.api.log.Log
import java.util.concurrent.atomic.AtomicReference

/** A long job polls this. Once it is true, the job's result is thrown away, so the job should stop early. */
interface Cancellation {
    val isCancelled: Boolean
}

/**
 * One worker thread, `blackaddons-<name>`, for work where only the newest job matters, like a planner that replans as
 * the player moves. [submit] cancels the job in progress and skips queued ones. Unlike with [OffThread], a replaced job
 * delivers nothing.
 */
class LatestJob(private val name: String) {

    private val worker = Workers.single(name)
    private val current = AtomicReference<Token?>()

    /**
     * [onResult] runs on the worker, and only if no newer job came in. Only publish the result there, and do not touch
     * Minecraft.
     */
    fun <T> submit(work: (Cancellation) -> T, onResult: (T) -> Unit) {
        val token = Token()
        current.getAndSet(token)?.isCancelled = true
        worker.execute {
            if (token.isCancelled) return@execute
            try {
                val value = work(token)
                if (!token.isCancelled) onResult(value)
            } catch (t: Throwable) {
                Log.main.warn("Background job '{}' failed", name, t)
            }
        }
    }

    /** Cancels the job in progress and anything queued. */
    fun cancel() {
        current.getAndSet(null)?.isCancelled = true
    }

    private class Token : Cancellation {
        @Volatile
        override var isCancelled = false
    }
}
