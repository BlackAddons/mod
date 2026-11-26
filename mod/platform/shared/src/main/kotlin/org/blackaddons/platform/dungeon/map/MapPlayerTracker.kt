package org.blackaddons.platform.dungeon.map

import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import org.blackaddons.api.Platform
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.map.MapPlayer
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.platform.mixin.world.MapItemSavedDataAccessor
import kotlin.time.TimeSource

/**
 * Player positions from the map's decorations. Separate from [HotbarMapScanner]: rooms come from color bytes, players
 * from decorations, they just share a packet.
 */
internal object MapPlayerTracker {

    /** Vanilla decorations are 256 units across the map, ours are 128. */
    private const val DECORATION_TO_MAP = 2f
    private const val DECORATION_ORIGIN = 128

    /** Sixteenths of a turn. */
    private const val DEGREES_PER_ROTATION_STEP = 22.5f

    fun update(mapData: MapItemSavedData) {
        val decorations = (mapData as? MapItemSavedDataAccessor)?.`blackaddons$getDecorations`() ?: return
        val selfName = Platform.player.name

        syncRoster(selfName)
        assignIcons(decorations, selfName)

        for (player in MapState.mapPlayers) {
            player.isDead = DungeonTracker.teammate(player.name)?.isDead ?: false
            if (player.isDead) continue

            val decoration = decorations[player.icon ?: continue] ?: continue
            moveSmoothly(
                player,
                targetX = (decoration.x + DECORATION_ORIGIN) / DECORATION_TO_MAP,
                targetZ = (decoration.y + DECORATION_ORIGIN) / DECORATION_TO_MAP,
                targetYaw = decoration.rot * DEGREES_PER_ROTATION_STEP,
            )
        }
    }

    // Only when the roster changes, so markers keep their lerp targets.
    private fun syncRoster(selfName: String?) {
        val names = DungeonTracker.dungeonTeammates.map { it.name }.toMutableList()
        if (selfName != null && names.none { it.equalsIgnoreCase(selfName) }) names.add(selfName)
        if (MapState.mapPlayers.map { it.name } == names) return

        MapState.mapPlayers.clear()
        names.forEach { MapState.mapPlayers.add(MapPlayer(it, it, null)) }
    }

    // Hypixel indexes teammate markers among the living. The player's own is the FRAME decoration.
    private fun assignIcons(decorations: Map<String, MapDecoration>, selfName: String?) {
        val livingTeammates = DungeonTracker.dungeonTeammatesNoSelf.filter { !it.isDead }

        decorations.forEach { (key, decoration) ->
            val owner = if (decoration.type == MapDecorationTypes.FRAME) {
                selfName
            } else {
                key.lastOrNull()?.digitToIntOrNull()?.takeIf { it in livingTeammates.indices }
                    ?.let { livingTeammates[it].name }
            } ?: return@forEach
            MapState.player(owner)?.icon = key
        }
    }

    private fun moveSmoothly(player: MapPlayer, targetX: Float, targetZ: Float, targetYaw: Float) {
        if (player.mapX == targetX && player.mapZ == targetZ && player.yaw == targetYaw) return

        player.lerpStartX = player.mapX
        player.lerpStartZ = player.mapZ
        player.lerpStartYaw = player.yaw
        player.targetX = targetX
        player.targetZ = targetZ
        player.targetYaw = targetYaw
        player.lerpStartedAt = TimeSource.Monotonic.markNow()
    }
}
