package org.blackaddons.api.host

import java.util.UUID

/** What a Mojang lookup found. */
sealed interface AccountLookup {
    data class Found(val uuid: UUID, val name: String) : AccountLookup

    /** No account has that name or id. */
    object NotFound : AccountLookup

    /** The lookup failed. The account may still exist. */
    object Unavailable : AccountLookup
}

/**
 * Minecraft accounts through the BlackAddons backend, which caches Mojang's answers. [onResult] runs on the client
 * thread.
 */
interface MojangAccounts {
    /** Players seen this session are answered right away, without a request. [name] is matched case-insensitively. */
    fun byName(name: String, onResult: (AccountLookup) -> Unit)

    fun byUuid(uuid: UUID, onResult: (AccountLookup) -> Unit)
}
