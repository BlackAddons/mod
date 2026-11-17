package org.blackaddons.api.skyblock.player

import org.blackaddons.api.HostApi

/**
 * Your live SkyBlock health, mana, defense and vitality, from the action bar, each the last value Hypixel showed.
 * Empty off SkyBlock and right after a world change; vitality clears soon after Hypixel stops showing it. Skill xp
 * gains arrive as `org.blackaddons.api.event.skyblock.SkillXpEvent`. Read it on the client thread.
 */
object SkyblockStats {

    @Volatile
    private var bound: SkyblockStatsProvider? = null

    private val provider: SkyblockStatsProvider
        get() = bound ?: error("SkyblockStats was read before the host bound its provider")

    /** Host only. */
    @HostApi
    fun bind(provider: SkyblockStatsProvider) {
        synchronized(this) {
            bound?.let { error("SkyblockStats is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    val current: ActionBarStats get() = provider.current
}

/** Host side of [SkyblockStats], bound once. */
interface SkyblockStatsProvider {
    val current: ActionBarStats
}
