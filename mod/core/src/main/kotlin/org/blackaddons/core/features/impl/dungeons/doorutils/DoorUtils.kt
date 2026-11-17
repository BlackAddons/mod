package org.blackaddons.core.features.impl.dungeons.doorutils

import org.blackaddons.api.Platform
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.skyblock.dungeon.DungeonDoor
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.map.DoorTile
import org.blackaddons.core.dungeon.map.DungeonTree
import org.blackaddons.core.dungeon.map.MapState
import java.awt.Color

object DoorUtils : Feature(
    description = "Box wither and blood doors in the world, colored by whether you hold the key.",
    name = "Door Utils",
) {
    val boxDoors by boolean("Box Doors", true)
        .withDescription("Draw 3D boxes on wither/blood doors in the world.")
    val boxDoorsMode by mode("Door Box Mode", "Filled Outline", listOf("Outline", "Fill", "Filled Outline"))
        .withDescription("How to render the door boxes.")
        .showIf { boxDoors.value }
    val doorNoKeyColor by color("No Key Color", Color(255, 0, 0, 100), allowAlpha = true)
        .withDescription("Door box color when you don't have a key.")
        .showIf { boxDoors.value }
    val doorKeyColor by color("Has Key Color", Color(0, 255, 0, 100), allowAlpha = true)
        .withDescription("Door box color when you have a key.")
        .showIf { boxDoors.value }
    val onlyNextDoorHasKeyColor by boolean("Only Next Door Has Key Color", false)
        .withDescription("Use the key color only on the next door along the blood rush; other doors keep the no key color.")
        .showIf { boxDoors.value }

    /** Also boxes doors next to unrevealed rooms. Never in legit, other editions replace it. */
    @Volatile
    var boxesUnrevealedDoor: (DungeonDoor) -> Boolean = { false }

    override fun init() {
        register<RenderWorldEvent> {
            if (!LocationUtils.inDungeonRooms) return@register
            if (boxDoors.value) renderDoorEsp()
        }
    }

    private fun renderDoorEsp() {
        val doors = MapState.dungeonList.filterIsInstance<DoorTile>().filter { tile ->
            !tile.opened && tile.type.needsKey &&
                (tile.state != RoomState.UNDISCOVERED || MapOverrides.showsUnrevealed || boxesUnrevealedDoor(tile))
        }
        val nextDoor = if (onlyNextDoorHasKeyColor.value) nextDoor(doors) else null
        for (tile in doors) {
            val hasKey = when (tile.type) {
                DoorType.WITHER -> DungeonTracker.witherKeys > 0
                DoorType.BLOOD -> DungeonTracker.bloodKeys > 0
                else -> true
            }
            val keyColor = hasKey && (!onlyNextDoorHasKeyColor.value || tile == nextDoor)
            val color = if (keyColor) doorKeyColor.value else doorNoKeyColor.value
            val outline = boxDoorsMode.value == "Outline" || boxDoorsMode.value == "Filled Outline"
            val fill = boxDoorsMode.value == "Fill" || boxDoorsMode.value == "Filled Outline"

            if (outline) {
                Platform.worldRenderer.boxEdges(
                    tile.x - 1.0, 69.0, tile.z - 1.0,
                    tile.x + 2.0, 73.0, tile.z + 2.0,
                    color, 2f, true,
                )
            }
            if (fill) {
                Platform.worldRenderer.filledBox(
                    tile.x - 1.0, 69.0, tile.z - 1.0,
                    tile.x + 2.0, 73.0, tile.z + 2.0,
                    color, true,
                )
            }
        }
    }

    private fun nextDoor(doors: List<DoorTile>): DoorTile? =
        DungeonTree.nextRushDoor()?.takeIf { it in doors } ?: doors.minByOrNull {
            val dx = it.x - Platform.player.x
            val dz = it.z - Platform.player.z
            dx * dx + dz * dz
        }
}
