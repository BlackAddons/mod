package org.blackaddons.core.backend

import org.blackaddons.api.Platform
import org.blackaddons.api.util.concurrent.Workers
import java.net.InetAddress
import java.net.URI
import java.security.Security
import kotlin.time.TimeSource

/**
 * Keeps the backend host resolved, so the player never waits on DNS. By default the JVM only caches DNS for 30s, and a
 * slow (VPN) resolver then stalls every action. We cache longer, resolve at startup and refresh in the background.
 */
object BackendDns {

    private const val CACHE_TTL_SECONDS = "600"

    /** Retry soon, the network might still be starting. */
    private const val NEGATIVE_TTL_SECONDS = "10"

    private const val REFRESH_TICKS = 20 * 300

    private val host: String? = runCatching { URI(Backend.BASE_URL).host }.getOrNull()

    fun init() {
        val host = host ?: return
        // JVM-wide. The only cost is we see a DNS change later.
        Security.setProperty("networkaddress.cache.ttl", CACHE_TTL_SECONDS)
        Security.setProperty("networkaddress.cache.negative.ttl", NEGATIVE_TTL_SECONDS)
        resolve(host)
        Platform.scheduler.every(REFRESH_TICKS) { resolve(host) }
    }

    fun refresh() {
        host?.let(::resolve)
    }

    private fun resolve(host: String) {
        Workers.start("dns") {
            val started = TimeSource.Monotonic.markNow()
            runCatching { InetAddress.getByName(host) }
                .onSuccess { NetLog.trace("Resolved {} to {} in {} ms", host, it.hostAddress, started.elapsedNow().inWholeMilliseconds) }
                .onFailure { NetLog.trace("Could not resolve {}: {}", host, it.toString()) }
        }
    }
}
