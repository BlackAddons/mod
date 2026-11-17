package org.blackaddons.api.skyblock

import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.LocationUtils.dungeonFloor
import org.blackaddons.api.skyblock.LocationUtils.inBoss
import org.blackaddons.api.skyblock.LocationUtils.isP3Sim
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.util.containsIgnoreCase

/**
 * Derived Skyblock location state - gate features on this instead of parsing the scoreboard. Area and floor
 * hold until world change; the rest is position tests against [DungeonGeometry].
 */
object LocationUtils {

    /** Matches the server brand, so it holds through proxies. */
    val onHypixel: Boolean
        get() = Platform.world.serverBrand?.containsIgnoreCase("hypixel") == true

    /** Skyblock sidebar objective present. */
    val inSkyblock: Boolean
        get() = Platform.skyblock.onSkyblock

    /** e.g. `"Hub"`, `"The Catacombs"`; null off Skyblock. */
    val area: String?
        get() = Platform.skyblock.location

    val dungeonFloor: DungeonFloor?
        get() = Platform.skyblock.dungeonFloor

    /** 0..7. */
    val dungeonFloorNumber: Int?
        get() = dungeonFloor?.number

    /** F7, M7 or P3Sim's copy - same devices, phases and blocks. Use [dungeonFloor] / [isP3Sim] to tell them apart. */
    val hasF7Boss: Boolean
        get() = isF7orM7 || isP3Sim

    val isMasterMode: Boolean
        get() = dungeonFloor?.isMasterMode == true

    val isF7orM7: Boolean
        get() = dungeonFloor?.isF7orM7 == true

    /** `p3sim.net`. */
    val isP3Sim: Boolean
        get() = Platform.world.serverAddress?.containsIgnoreCase("p3sim.net") == true

    val inDungeon: Boolean
        get() = dungeonFloor != null || area == "The Catacombs" || isP3Sim

    /** The Rift has its own health system; our HP math doesn't apply. */
    val inRift: Boolean
        get() = Platform.skyblock.inRift

    val inBoss: Boolean
        get() {
            if (!Platform.player.isInGame) return false
            val floor = dungeonFloorNumber ?: return isP3Sim
            return DungeonGeometry
                .inBossRoom(floor, Platform.player.x, Platform.player.y, Platform.player.z)
        }

    /** In the rooms of a dungeon, not in its boss fight. */
    val inDungeonRooms: Boolean
        get() = inDungeon && !inBoss

    /** Necron phase by altitude while [inBoss]; [F7Phase.GOLDOR] is the device room. */
    val f7Phase: F7Phase?
        get() {
            if (!Platform.player.isInGame) return null
            if (!hasF7Boss || !inBoss) return null
            return DungeonGeometry.f7Phase(Platform.player.y)
        }

    /** While [inBoss] on an F7 boss, or null. */
    val f7SubLocation: F7SubLocation?
        get() {
            if (!Platform.player.isInGame || !hasF7Boss || !inBoss) return null
            return DungeonGeometry
                .f7SubLocation(Platform.player.x, Platform.player.y, Platform.player.z)
        }

    /** 1..4, or null. */
    val p3Section: Int?
        get() {
            if (f7Phase != F7Phase.GOLDOR || !Platform.player.isInGame) return null
            return DungeonGeometry
                .p3Section(Platform.player.x, Platform.player.y, Platform.player.z)
        }
}
