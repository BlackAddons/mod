package org.blackaddons.core.soloclears

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.stripFormatting
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.PaulBonus
import org.blackaddons.core.dungeon.ScoreCalculation
import org.blackaddons.core.dungeon.map.DoorTile
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.RoomTile
import org.blackaddons.core.dungeon.map.UniqueRoom
import org.blackaddons.core.features.impl.dungeons.map.DungeonMap

/**
 * The map as last seen, written once at the finish (rooms keep their last state). Half-room grid cells, so doors sit
 * between rooms. Empty cells are left out.
 */
internal object RunMapSnapshot {

    val available: Boolean get() = MapState.uniqueRooms.isNotEmpty()

    fun apply(record: JsonObject) {
        val ids = MapState.uniqueRooms.values.withIndex().associate { (index, room) -> room to index + 1 }

        record.addProperty("rows", MapState.GRID_SIZE)
        record.addProperty("cols", MapState.GRID_SIZE)
        extent()?.let { (width, height) ->
            record.addProperty("width", width)
            record.addProperty("height", height)
        }
        record.add("rooms", JsonArray().apply { ids.forEach { (room, id) -> add(room(room, id)) } })
        record.add("cells", cells(ids))
    }

    // Counts unidentified rooms too: the hotbar map draws rooms whose chunks never loaded, and cells() drops them.
    private fun extent(): Pair<Int, Int>? {
        val tiles = MapState.gridRange.step(2).flatMap { row ->
            MapState.gridRange.step(2).filter { column -> MapState.tileAt(column, row) is RoomTile }.map { it to row }
        }
        if (tiles.isEmpty()) return null

        // Even indices are rooms, so two cells per tile.
        val width = (tiles.maxOf { it.first } - tiles.minOf { it.first }) / 2 + 1
        val height = (tiles.maxOf { it.second } - tiles.minOf { it.second }) / 2 + 1
        return width to height
    }

    private fun room(room: UniqueRoom, id: Int): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", room.name)
        addProperty("type", room.data.type.name)
        addProperty("state", room.mainRoom.state.name)
        addProperty("found", room.foundSecrets)
        addProperty("secrets", room.data.secrets)
        room.rotation?.let { addProperty("rot", it) }
        room.clayPos?.let { (clayX, _, clayZ) ->
            addProperty("clayX", clayX)
            addProperty("clayZ", clayZ)
        }
    }

    private fun cells(ids: Map<UniqueRoom, Int>): JsonArray = JsonArray().apply {
        for (row in MapState.gridRange) {
            for (column in MapState.gridRange) {
                val cell = when (val tile = MapState.tileAt(column, row)) {
                    is RoomTile -> ids[tile.uniqueRoom]?.let { cell(row, column, it, null) }
                    is DoorTile -> cell(row, column, 0, tile.type.name)
                    else -> null
                }
                cell?.let(::add)
            }
        }
    }

    private fun cell(row: Int, column: Int, roomId: Int, door: String?): JsonObject = JsonObject().apply {
        addProperty("r", row)
        addProperty("c", column)
        addProperty("room", roomId)
        door?.let { addProperty("door", it) }
    }
}

/** Raw score components. The server computes the score and checks the total the finish record claims. */
internal object RunScoreInput {

    fun build(floor: String): JsonObject = JsonObject().apply {
        addProperty("floor", floor)
        addProperty("completedRooms", ScoreCalculation.completedRooms)
        addProperty("clearedPercentage", ScoreCalculation.clearedPercentage)
        addProperty("secretPercentage", ScoreCalculation.secretPercentage)
        addProperty("foundSecrets", ScoreCalculation.foundSecrets)
        addProperty("totalSecrets", ScoreCalculation.estimatedTotalSecrets())
        addProperty("uniqueRooms", MapState.uniqueRooms.size)
        addProperty("deaths", ScoreCalculation.deathCount)
        addProperty("unresolvedPuzzles", ScoreCalculation.unresolvedPuzzles())
        addStrings("unresolvedPuzzleNames", ScoreCalculation.unresolvedPuzzleNames())
        addStrings("puzzleNames", ScoreCalculation.puzzleNames())
        addProperty("crypts", ScoreCalculation.cryptsCount)
        addProperty("secondsElapsed", ScoreCalculation.secondsElapsed)
        addProperty("bloodDone", ScoreCalculation.bloodDone)
        addProperty("inBoss", LocationUtils.inBoss)
        addProperty("mimicKilled", ScoreCalculation.mimicKilled)
        addProperty("princeKilled", ScoreCalculation.princeKilled)
        addProperty("batKilled", ScoreCalculation.batKilled)
        addProperty(
            "paulActive",
            PaulBonus.active(DungeonMap.forcePaul.value, DungeonMap.forceDisablePaul.value),
        )
    }
}

/**
 * Sidebar and tab list at the time of asking. They're the only proof a run was solo (Hypixel's API doesn't say).
 * Missing snapshots get noted, not refused.
 */
internal object RunPartySnapshot {

    private const val TAB_LINES = 24

    private val TAB_PARTY_HEADER_REGEX = Regex("""Party \((\d+)\)""")

    fun apply(record: JsonObject) {
        record.addStrings("sidebar", Platform.skyblock.sidebarLines)
        record.addStrings("tablist", Platform.skyblock.tabLines.take(TAB_LINES))
    }

    fun looksSolo(): Boolean {
        if (DungeonTracker.dungeonTeammatesNoSelf.isNotEmpty()) return false

        val tab = Platform.skyblock.tabLines
        for (line in tab) {
            val stripped = line.stripFormatting()
            val match = TAB_PARTY_HEADER_REGEX.find(stripped) ?: continue
            val size = match.groupValues[1].toIntOrNull() ?: continue
            if (size > 1) return false
        }

        val sidebar = Platform.skyblock.sidebarLines
        val sidebarHasSolo = sidebar.any { it.stripFormatting().trim().equalsIgnoreCase("Solo") }
        val tabHasParty1 = tab.any {
            val match = TAB_PARTY_HEADER_REGEX.find(it.stripFormatting())
            match?.groupValues?.getOrNull(1) == "1"
        }

        return sidebarHasSolo || tabHasParty1
    }
}
