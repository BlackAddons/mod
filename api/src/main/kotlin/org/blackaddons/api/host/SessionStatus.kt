package org.blackaddons.api.host

import java.util.UUID

/** Addon-safe view of the account. Never exposes the token or Mojang join (first-party only, see `MojangSession`). */
interface SessionStatus {
    /** Available at the main menu, unlike `Platform.player`. Null until known. */
    val username: String?

    val uuid: UUID?

    /**
     * Host-performed Mojang ownership check; [callback] runs on the client thread. Only trustworthy within
     * this client - it gives no proof a remote backend can verify.
     */
    fun verifyOwnership(callback: (SessionOwnership) -> Unit)
}

enum class SessionOwnership {
    VERIFIED,

    /** Offline/cracked account. */
    OFFLINE,

    /** Mojang or backend unreachable, or session not ready. Retry later. */
    UNAVAILABLE,
}
