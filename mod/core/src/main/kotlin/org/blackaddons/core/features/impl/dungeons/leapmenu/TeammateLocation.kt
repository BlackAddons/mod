package org.blackaddons.core.features.impl.dungeons.leapmenu

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatChannel
import org.blackaddons.api.chat.PlayerMessage
import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.skyblock.F7SubLocation
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.core.dungeon.map.MapState
import java.util.TreeMap
import java.util.UUID

private val tr = I18n.scope("blackaddons.leap_menu")

internal object TeammateLocation {

    private val lastBoss = mutableMapOf<String, String>()
    private val lastSpot = TreeMap<String, F7SubLocation>(String.CASE_INSENSITIVE_ORDER)

    fun of(name: String, uuid: UUID?): String? {
        val box = uuid?.let(Platform.world::entityBox)
        if (box != null) {
            val center = box.center()
            if (LocationUtils.hasF7Boss) remember(name, DungeonGeometry.f7SubLocation(center.x, box.min.y, center.z))
            return boss(LocationUtils.dungeonFloor, LocationUtils.isP3Sim, center.x, box.min.y, center.z)
                ?.also { lastBoss[name.lowercase()] = it }
                ?: room(center.x, center.z)
        }
        /* In the boss, the hotbar map marker is stale and stays in the last cleared room. */
        if (LocationUtils.inBoss) return lastBoss[name.lowercase()]
        if (!MapState.calibrated) return null
        val marker = MapState.player(name) ?: return null
        val (x, z) = MapState.mapToWorld(marker.mapX, marker.mapZ)
        return room(x, z)
    }

    fun boss(floor: DungeonFloor?, p3Sim: Boolean, x: Double, y: Double, z: Double): String? {
        if (!p3Sim && (floor == null || !DungeonGeometry.inBossRoom(floor.number, x, y, z))) return null
        if (!p3Sim && floor?.isF7orM7 != true) return tr("boss", "%s Boss", floor?.label ?: "")
        val phase = DungeonGeometry.f7Phase(y)
        DungeonGeometry.f7SubLocation(x, y, z)?.let { return tr("phase_sublocation", "P%d %s", phase.number, it.label) }
        val section = if (phase == F7Phase.GOLDOR) DungeonGeometry.p3Section(x, y, z) else null
        return if (section != null) tr("phase_section", "P%d S%d", phase.number, section) else tr("phase", "P%d", phase.number)
    }

    fun track() {
        if (!LocationUtils.inBoss) return
        for (player in Dungeon.team.others) of(player.name, Platform.world.playerUuid(player.name))
    }

    fun onChat(line: String) {
        if (!LocationUtils.isP3Sim && !LocationUtils.isF7orM7) return
        val (name, spot) = posMessage(line) ?: return
        lastSpot[name] = spot
        val center = spot.region.center()
        boss(LocationUtils.dungeonFloor, LocationUtils.isP3Sim, center.x, spot.region.min.y, center.z)
            ?.let { lastBoss[name.lowercase()] = it }
    }

    /* Last seen there: by position in render distance, otherwise by their "at ss" party message. */
    fun at(spot: F7SubLocation): String? = lastSpot.entries.firstOrNull { (name, seen) ->
        seen == spot && Dungeon.team.player(name)?.isDead != true && !name.equalsIgnoreCase(Platform.player.name)
    }?.key

    fun reset() {
        lastBoss.clear()
        lastSpot.clear()
    }

    internal fun posMessage(line: String): Pair<String, F7SubLocation>? {
        val message = PlayerMessage.of(line)?.takeIf { it.channel == ChatChannel.PARTY } ?: return null
        val name = message.player
        val spot = message.text.trim().trimEnd('!', '.').lowercase().removePrefix("at ").trim().replace("early enter ", "ee")
        val location = F7SubLocation.entries.firstOrNull { it.label.equalsIgnoreCase(spot) } ?: return null
        return name to location
    }

    private fun remember(name: String, spot: F7SubLocation?) {
        if (spot == null) lastSpot.remove(name) else lastSpot[name] = spot
    }

    private fun room(x: Double, z: Double): String? =
        Dungeon.layout.roomAt(x, z)?.name?.takeIf { it != UNKNOWN_ROOM }

    private const val UNKNOWN_ROOM = "Unknown"
}
