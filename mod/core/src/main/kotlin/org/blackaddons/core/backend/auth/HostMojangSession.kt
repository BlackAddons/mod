package org.blackaddons.core.backend.auth

/**
 * Holds [MojangSession] for first-party code. The platform sets it. It's not on `Platform`, so addons (compiled against
 * :api only) can't authenticate as the user.
 */
object HostMojangSession {
    lateinit var session: MojangSession
}
