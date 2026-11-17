package org.blackaddons.core.backend.auth

import java.util.UUID

/**
 * Privileged Mojang "join" that proves ownership to a backend (`hasJoined` -> player JWT). First-party only. Addons use
 * [org.blackaddons.api.host.SessionStatus]. The platform implements it, see [HostMojangSession].
 */
interface MojangSession {
    val username: String?
    val uuid: UUID?

    /**
     * Joins Mojang's session server with [serverId]. Blocking. Tells a rejection apart from an unreachable service, so
     * an outage doesn't look like a fake account.
     */
    fun joinServer(serverId: String): JoinResult
}

enum class JoinResult {
    JOINED,

    /** Offline/cracked account. */
    REJECTED,

    UNAVAILABLE,
}
