package org.blackaddons.core.profile

import java.util.UUID

/** A player as the game's own Mojang lookups know them. [textures] and [signature] are the signed skin property. */
class GameAccount(val uuid: UUID, val name: String, val textures: String?, val signature: String?)

/**
 * The game's own Mojang lookups. They go out from the player's connection, under the player's own rate limit and
 * through the game's caches, so neither our backend nor a single server IP sits between all players and Mojang.
 * Blocking. Null both when Mojang has no such player and when it couldn't be reached.
 */
interface GameProfileLookup {
    fun byName(name: String): GameAccount?

    fun byUuid(uuid: UUID): GameAccount?

    /** Only from the game's name cache (`usercache.json`). It makes no request, so it's safe to call anywhere. */
    fun knownName(uuid: UUID): String?
}

/**
 * Set by the platform. It's not on `Platform`: addons get the same data through `Platform.mojang`, which asks this
 * first.
 */
object HostGameProfiles {
    @Volatile
    var lookup: GameProfileLookup? = null
}
