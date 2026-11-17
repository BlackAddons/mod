package org.blackaddons.platform.ui.render

import org.blackaddons.api.Platform
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.platform.ui.render.RemoteImages.poll
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches http(s) image bytes off-thread. [poll] each frame until it's ready. Decoding and caching are the renderer's
 * job.
 */
internal object RemoteImages {

    sealed interface Fetch {
        /** Draw nothing this frame. */
        data object Pending : Fetch

        /** Remember the miss, don't retry. */
        data object Failed : Fetch

        class Ready(val bytes: ByteArray) : Fetch
    }

    // Fetch threads write, the render thread drains, in-flight dedupes.
    private val fetched = ConcurrentHashMap<String, ByteArray>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val pool = Workers.pool("skin-fetch", 2)

    fun poll(url: String): Fetch {
        fetched.remove(url)?.let {
            inFlight.remove(url)
            return Fetch.Ready(it)
        }
        if (failed.remove(url)) {
            inFlight.remove(url)
            return Fetch.Failed
        }
        if (inFlight.add(url)) {
            pool.execute {
                // Shared client: pooling, warm DNS, one size cap and timeouts. Failures get logged there.
                val bytes = Platform.http.bytesBlocking(url)
                if (bytes != null) fetched[url] = bytes else failed.add(url)
            }
        }
        return Fetch.Pending
    }
}
