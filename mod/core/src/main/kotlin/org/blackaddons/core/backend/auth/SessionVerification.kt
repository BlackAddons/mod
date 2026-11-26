package org.blackaddons.core.backend.auth

import org.blackaddons.api.Platform
import org.blackaddons.api.host.SessionOwnership
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.log.Log

/**
 * Async [BackendSession.ownership] for addons. Runs the handshake on a worker and answers on the client thread. Only
 * the verdict is exposed.
 */
object SessionVerification {
    private val executor = Workers.single("verify")

    fun verifyAsync(callback: (SessionOwnership) -> Unit) {
        executor.execute {
            val result = runCatching { BackendSession.ownership() }
                .onFailure { Log.main.warn("Session verification failed", it) }
                .getOrDefault(SessionOwnership.UNAVAILABLE)
            Platform.scheduler.schedule(0) { callback(result) }
        }
    }
}
