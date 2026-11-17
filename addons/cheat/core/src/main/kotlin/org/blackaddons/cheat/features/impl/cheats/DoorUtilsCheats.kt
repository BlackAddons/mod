package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.util.packInts
import org.blackaddons.api.util.packedHigh
import org.blackaddons.api.util.packedLow
import org.blackaddons.api.world.BlockAppearance
import org.blackaddons.api.world.BlockAppearanceSource
import org.blackaddons.api.world.BlockAppearances
import org.blackaddons.core.features.impl.dungeons.doorutils.DoorUtils

/** Cheat-edition [DoorUtils]: boxes for unrevealed doors and render-only door replacement. */
object DoorUtilsCheats : FeatureExtension(DoorUtils, group = "Cheats") {
    private const val REPLACE_OFF = "Off"
    private const val DOOR_MIN_Y = 69
    private const val DOOR_MAX_Y = 72

    val highlightAllDoors by boolean("Highlight All Doors", false)
        .withDescription("Highlight every unopened door instead of only the next ones.")
        .showIf { DoorUtils.boxDoors.value }
    private val replaceDoors by mode("Replace Doors", REPLACE_OFF, listOf(REPLACE_OFF, "Glass", "Barrier"))
        .withDescription("Draw wither and blood doors as glass, or hide them as barriers. Only changes how they look.")

    private data class ReplacedColumn(val sourceBlockId: String, val appearance: BlockAppearance)

    /** Every x/z column of each replaced door's 3x3, so meshing threads only need one lookup. */
    @Volatile
    private var replacedColumns: Map<Long, ReplacedColumn> = emptyMap()

    private val appearanceSource = object : BlockAppearanceSource {
        override fun covers(x: Int, y: Int, z: Int): Boolean =
            y in DOOR_MIN_Y..DOOR_MAX_Y && replacedColumns.containsKey(packInts(x, z))

        override fun appearanceAt(x: Int, y: Int, z: Int, sourceBlockId: String, originalIsAir: Boolean): BlockAppearance? =
            replacedColumns[packInts(x, z)]?.takeIf { it.sourceBlockId == sourceBlockId }?.appearance
    }

    override fun init() {
        DoorUtils.boxesUnrevealedDoor = { highlightAllDoors.value || Dungeon.layout.isFairyExit(it) }
        BlockAppearances.register(appearanceSource)
        EventBus.subscribe<ClientTickEvent.Post> { publishReplacedColumns(collectReplacedColumns()) }
    }

    private fun collectReplacedColumns(): Map<Long, ReplacedColumn> {
        if (!DoorUtils.isRunning) return emptyMap()
        val blockId = when (replaceDoors.value) {
            "Glass" -> "minecraft:glass"
            "Barrier" -> "minecraft:barrier"
            else -> return emptyMap()
        }
        if (!LocationUtils.inDungeonRooms) return emptyMap()
        val appearance = BlockAppearance(blockId)
        val columns = HashMap<Long, ReplacedColumn>()
        for (door in Dungeon.layout.doors) {
            if (door.opened || !door.type.needsKey) continue
            val sourceBlockId = Platform.world.blockIdAt(door.x, DOOR_MIN_Y, door.z) ?: continue
            if (sourceBlockId == "minecraft:air") continue
            val column = ReplacedColumn(sourceBlockId, appearance)
            for (dx in -1..1) for (dz in -1..1) columns[packInts(door.x + dx, door.z + dz)] = column
        }
        return columns
    }

    private fun publishReplacedColumns(next: Map<Long, ReplacedColumn>) {
        val previous = replacedColumns
        if (previous == next) return
        replacedColumns = next
        for (key in previous.keys + next.keys) {
            if (previous[key] == next[key]) continue
            val x = key.packedHigh()
            val z = key.packedLow()
            for (y in DOOR_MIN_Y..DOOR_MAX_Y) Platform.world.redrawBlock(x, y, z)
        }
    }
}
