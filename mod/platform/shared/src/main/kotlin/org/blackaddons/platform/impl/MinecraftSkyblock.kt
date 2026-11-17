package org.blackaddons.platform.impl

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerTeam
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.HypixelSidebar
import org.blackaddons.api.skyblock.SkyblockHost
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.platform.util.TickCache

/**
 * [SkyblockHost] from the scoreboard and tab list. Location is latched: each tick keeps what's announced
 * (`SBScoreboard`, `Area:`/`Dungeon:`, profile, Rift title, floor) and only a world change clears it. Hypixel rewrites
 * these lines mid-session (M7 P5 replaces the floor line), and a missing line means no change.
 */
object MinecraftSkyblock : SkyblockHost {

    private const val SKYBLOCK_OBJECTIVE = "SBScoreboard"

    private val mc get() = Minecraft.getInstance()

    @Volatile override var onSkyblock = false
        private set

    @Volatile override var location: String? = null
        private set

    @Volatile override var profile: String? = null
        private set

    @Volatile override var dungeonFloor: DungeonFloor? = null
        private set

    @Volatile override var inRift = false
        private set

    override val accountId: String
        get() = mc.user.profileId.toString()

    init {
        EventBus.subscribe<ClientTickEvent.Pre> { latch() }
        EventBus.subscribe<WorldChangeEvent> { reset() }
    }

    private fun latch() {
        if (!onSkyblock) onSkyblock = sidebarObjective()?.name == SKYBLOCK_OBJECTIVE
        if (!onSkyblock) return
        val tab = tabLinesCache.get()
        HypixelSidebar.area(tab)?.let { location = it }
        HypixelSidebar.profile(tab)?.let { profile = it }
        if (dungeonFloor == null) dungeonFloor = HypixelSidebar.dungeonFloor(sidebarLinesCache.get())
        if (!inRift) inRift = HypixelSidebar.isRift(sidebarObjective()?.displayName?.string)
    }

    private fun reset() {
        onSkyblock = false
        location = null
        profile = null
        dungeonFloor = null
        inRift = false
    }

    override val tabLines: List<String>
        get() = if (onSkyblock) tabLinesCache.get() else emptyList()

    override val sidebarLines: List<String>
        get() = if (onSkyblock) sidebarLinesCache.get() else emptyList()

    private fun sidebarObjective(): Objective? = mc.level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)

    private val tabLinesCache = TickCache { buildTabLines() }

    /** Sidebar rows top to bottom, each team prefix + holder + suffix as plain text, like the vanilla HUD. */
    private val sidebarLinesCache = TickCache { buildSidebarLines() }

    private fun buildTabLines(): List<String> {
        val connection = mc.player?.connection ?: return emptyList()
        return connection.onlinePlayers
            .sortedBy { it.profile.name }
            .mapNotNull { it.tabListDisplayName?.string }
    }

    private fun buildSidebarLines(): List<String> {
        val scoreboard = mc.level?.scoreboard ?: return emptyList()
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()
        return scoreboard.listPlayerScores(objective)
            .sortedByDescending { it.value() }
            .take(15)
            .map { entry ->
                val team = scoreboard.getPlayersTeam(entry.owner())
                val name = entry.ownerName()
                if (team == null) name.string else PlayerTeam.formatNameForTeam(team, name).string
            }
    }
}
