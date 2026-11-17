package org.blackaddons.api.skyblock

import org.blackaddons.api.skyblock.dungeon.DungeonFloor

/** Player's Skyblock context. Fields hold what the sidebar/tab list last announced and only clear on world change. */
interface SkyblockHost {
    val onSkyblock: Boolean

    /** e.g. `"Hub"`, `"Private Island"`; null off Skyblock or not yet received. */
    val location: String?

    /** Cute name (e.g. `"Mango"`); per-profile caches key off it. */
    val profile: String?

    /** Account UUID; with [profile] it scopes per-character state. */
    val accountId: String?

    /** From the sidebar's `"The Catacombs (Fx)"` line. */
    val dungeonFloor: DungeonFloor?

    /** From the sidebar title ("THE RIFT"). The Rift has its own HP, so stat features should stand down. */
    val inRift: Boolean

    /** Plain text. */
    val tabLines: List<String> get() = emptyList()

    /** Plain text. */
    val sidebarLines: List<String> get() = emptyList()
}
