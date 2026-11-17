package org.blackaddons.core.backend

import org.blackaddons.core.backend.auth.BackendSession
import org.blackaddons.core.backend.gateway.Gateway

internal object Network {

    /** Reopens only the socket, with no backoff. */
    fun restartSocket() {
        NetLog.trace("Network: socket restart requested")
        Gateway.restart()
    }

    /**
     * Makes a new HTTP client, session and latches, does a DNS lookup, then opens the socket. The JVM address cache
     * ([BackendDns], 10 min) can't be reset from here.
     */
    fun restartAll() {
        NetLog.trace("Network: full restart requested")
        Http.reset()
        BackendSession.reset()
        BackendDns.refresh()
        Gateway.restart()
    }
}
