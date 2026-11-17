package org.blackaddons.api.util.concurrent
import org.blackaddons.api.log.Log

/**
 * Runs one blocking job off the client thread and always delivers a result, so a loading screen can't hang if
 * the worker throws. See [org.blackaddons.api.Platform.scheduler] for the opposite direction.
 */
object OffThread {

    /**
     * Runs [work] on a daemon thread `blackaddons-[name]`, then calls [onResult] exactly once with the result
     * or [onFailure]'s value. [onResult] runs on the worker - only publish (assign a `@Volatile`), never
     * touch Minecraft. Keep [onFailure] trivial; it isn't guarded.
     */
    fun <T> run(
        name: String,
        work: () -> T,
        onFailure: (Throwable) -> T,
        onResult: (T) -> Unit,
    ) {
        val body = {
            val value = try {
                work()
            } catch (t: Throwable) {
                Log.main.warn("Background task '{}' failed", name, t)
                onFailure(t)
            }
            try {
                onResult(value)
            } catch (t: Throwable) {
                Log.main.error("Background task '{}' could not publish its result", name, t)
            }
        }
        Workers.start(name, body)
    }
}
