package org.blackaddons.api.util.concurrent

import java.util.concurrent.ConcurrentHashMap

/**
 * Values that are loaded off the client thread the first time someone asks for them, then kept for the rest of the
 * game. It is safe to ask every frame from any thread: answers come from memory, and at most one [load] runs per key at
 * a time. A [load] that returns null or throws counts as a failure, and that key is tried again only after
 * [retryAfterMs].
 */
class LoadOnce<K : Any, V : Any>(
    private val name: String,
    private val retryAfterMs: Long = NEVER,
    /** Runs on the worker after a successful load. Only publish from here. */
    private val onLoaded: (K, V) -> Unit = { _, _ -> },
    private val load: (K) -> V?,
) {
    private val ready = ConcurrentHashMap<K, V>()
    private val failedAt = ConcurrentHashMap<K, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<K>()

    /** Null while the value is loading or after a failure. The first ask starts the load. */
    operator fun get(key: K): V? {
        ready[key]?.let { return it }
        failedAt[key]?.let { if (System.currentTimeMillis() - it < retryAfterMs) return null }
        if (!inFlight.add(key)) return null
        OffThread.run(
            name = name,
            work = { load(key) },
            onFailure = { null },
            onResult = { value ->
                if (value == null) {
                    failedAt[key] = System.currentTimeMillis()
                } else {
                    failedAt -= key
                    ready[key] = value
                    onLoaded(key, value)
                }
                inFlight -= key
            },
        )
        return null
    }

    fun isLoading(key: K): Boolean = key in inFlight

    companion object {
        /** A failed key is never tried again during this game. */
        const val NEVER = Long.MAX_VALUE
    }
}
