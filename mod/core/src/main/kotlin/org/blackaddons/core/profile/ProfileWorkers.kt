package org.blackaddons.core.profile

import org.blackaddons.api.util.concurrent.Workers
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * Threads for the parts of a lookup that don't have to wait for each other: downloads alongside the fetch, and NBT
 * decoding.
 */
internal object ProfileWorkers {

    // Data fixing is CPU work, so leave some cores to the game.
    private val THREADS = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)

    private val pool: ExecutorService = Workers.pool("profile", THREADS)

    // An explicit Callable, because a bare lambda can resolve to the Runnable overload and lose the result.
    fun <T> submit(task: () -> T): Future<T> = pool.submit(Callable { task() })
}
