package org.blackaddons.platform.dungeon.map

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.api.skyblock.dungeon.RoomShape
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.map.DoorTile
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.RoomData
import org.blackaddons.core.dungeon.map.RoomPrediction
import org.blackaddons.core.dungeon.map.RoomTile
import org.blackaddons.core.dungeon.map.Tile
import org.blackaddons.core.dungeon.map.Unknown
import org.blackaddons.core.features.impl.dungeons.map.DungeonMap
import org.blackaddons.platform.event.impl.MapItemDataUpdateEvent

/** Reads room states from hotbar map colors, calibrates map coords, and hands decorations to [MapPlayerTracker]. */
object HotbarMapScanner {
    private var centerColors = ByteArray(MapState.GRID_TILE_COUNT)
    private var sideColors = ByteArray(MapState.GRID_TILE_COUNT)
    private var cachedTiles: Array<Tile?> = Array(MapState.GRID_TILE_COUNT) { null }
    private var dungeonMapId: MapId? = null

    val halfRoom: Int get() = MapState.mapRoomSize / 2
    val halfTile: Int get() = halfRoom + 2
    val quarterRoom: Int get() = halfRoom / 2
    val startX: Int get() = MapState.startCorner.first + halfRoom
    val startY: Int get() = MapState.startCorner.second + halfRoom

    fun init() {
        EventBus.subscribe<WorldChangeEvent> {
            resetColorCaches()
            dungeonMapId = null
        }

        EventBus.subscribe<MapItemDataUpdateEvent> {
            onMapPacket(event.packet)
        }
    }

    fun calibrate() {
        resetColorCaches()
    }

    fun onMapPacket(packet: ClientboundMapItemDataPacket) {
        if (!LocationUtils.inDungeon) return
        if (DungeonTracker.dungeonEnded) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        val hotbarItem = mc.player?.inventory?.getItem(8)
        val hotbarMapId = hotbarItem?.get(DataComponents.MAP_ID)
        if (hotbarMapId != null) {
            dungeonMapId = hotbarMapId
        }

        val activeMapId = dungeonMapId ?: hotbarMapId
        if (activeMapId != null && packet.mapId != activeMapId) return

        val mapData = level.getMapData(packet.mapId) ?: (if (activeMapId != null) level.getMapData(activeMapId) else null) ?: return
        MapPlayerTracker.update(mapData)

        var nonZero = 0
        for (b in mapData.colors) {
            if (b.toInt() != 0) nonZero++
        }
        if (nonZero == 0) return

        if (!MapState.mapDataCalibrated) {
            if (!calibrateMap(mapData)) return
            MapState.calibrated = true
            MapState.mapDataCalibrated = true
            DungeonMap.debug {
                "[DungeonMap] Calibration: result=${MapState.calibrated}, floor=${LocationUtils.dungeonFloorNumber}, " +
                    "roomSize=${MapState.mapRoomSize}, startCorner=${MapState.startCorner}, coordMult=${MapState.coordMultiplier}"
            }
            dungeonMapId = packet.mapId
        }

        if (!LocationUtils.inBoss) {
            updateRooms(mapData)
        }
    }

    private fun calibrateMap(mapData: MapItemSavedData): Boolean {
        val (start, size) = findEntranceCorner(mapData)
        if (size != 16 && size != 18) {
            DungeonMap.debug { "[DungeonMap] Calibration failed: invalid green size $size at index $start" }
            return false
        }

        MapState.applyCalibration(MapState.calibrationFor(LocationUtils.dungeonFloorNumber, size, start))
        RoomPrediction.configure(LocationUtils.dungeonFloorNumber, MapState.startCorner)

        calibrate()
        return true
    }

    private fun findEntranceCorner(mapData: MapItemSavedData): Pair<Int, Int> {
        var currLength = 0
        var start = 0
        var greenStart = -1
        var greenLength = -1

        for (i in mapData.colors.indices) {
            val color = mapData.colors[i].toInt() and 0xFF
            if (color == 30) {
                if (currLength++ == 0) start = i
            } else {
                if (currLength >= 16) {
                    greenStart = start
                    greenLength = currLength
                    break
                }
                currLength = 0
            }
        }
        if (currLength >= 16 && greenStart == -1) {
            greenStart = start
            greenLength = currLength
        }
        return greenStart to greenLength
    }

    fun updateMap(mapData: MapItemSavedData) {
        cachedTiles = Array(MapState.GRID_TILE_COUNT) { null }
        val sX = startX
        val sY = startY
        val hTile = halfTile
        val hRoom = halfRoom

        for (x in MapState.gridRange) for (y in MapState.gridRange) {
            val mapX = sX + x * hTile
            val mapY = sY + y * hTile
            if (mapX >= 128 || mapY >= 128) continue

            centerColors[MapState.indexOf(x, y)] = mapData.colors[mapY * 128 + mapX]

            val sideIndex = if (x % 2 == 0 && y % 2 == 0) {
                val topX = mapX - hRoom
                val topY = mapY - hRoom
                topY * 128 + topX
            } else {
                val horizontal = y % 2 == 1
                if (horizontal) mapY * 128 + mapX - 4
                else (mapY - 4) * 128 + mapX
            }
            sideColors[MapState.indexOf(x, y)] = mapData.colors.getOrElse(sideIndex) { 0 }
        }
    }

    fun updateRooms(mapData: MapItemSavedData) {
        if (LocationUtils.inBoss) return
        updateMap(mapData)

        val level = Minecraft.getInstance().level
        for (x in MapState.gridRange) for (z in MapState.gridRange) updateTile(x, z, level)
        MapState.joinConnectedRooms(::getConnected)
        recordVisibleRoomEntries()
        RoomPrediction.refresh()
    }

    private fun recordVisibleRoomEntries() {
        for (gridX in MapState.gridRange step 2) for (gridZ in MapState.gridRange step 2) {
            val room = getTile(gridX, gridZ) as? RoomTile ?: continue
            if (room.state == RoomState.UNOPENED) MapState.recordVisibleRoomEntry(gridX, gridZ)
        }
    }

    private fun updateTile(gridX: Int, gridZ: Int, level: Level?) {
        val tile = MapState.tileAt(gridX, gridZ) ?: return
        val mapTile = getTile(gridX, gridZ)

        if (tile is Unknown) {
            discoverTile(gridX, gridZ, mapTile)
            return
        }

        updateTileState(tile, mapTile, gridX, gridZ)
        if (tile is RoomTile && mapTile is RoomTile) updateRoomData(tile, mapTile, gridX, gridZ)
        if (tile is DoorTile) updateDoor(tile, mapTile, gridX, gridZ, level)
    }

    private fun discoverTile(gridX: Int, gridZ: Int, mapTile: Tile) {
        if (mapTile is Unknown) return
        MapState.replaceTile(gridX, gridZ, mapTile)
        DungeonMap.debug {
            "[HotbarMapScanner] Discovered tile from map packet at [c=$gridX, r=$gridZ]: type=${if (mapTile is RoomTile) mapTile.data.type else if (mapTile is DoorTile) mapTile.type else "Unknown"}, state=${mapTile.state}"
        }
        if (mapTile is RoomTile && mapTile.data.name != "Unknown") mapTile.addToUnique(gridZ, gridX)
    }

    private fun updateTileState(tile: Tile, mapTile: Tile, gridX: Int, gridZ: Int) {
        // Same states change nothing (RoomTile drops same-value writes), this just saves work on 121 tiles per packet.
        if (tile.state == mapTile.state) return
        if (
            MapOverrides.keepsOptimisticRooms &&
            tile is RoomTile &&
            (tile.state == RoomState.DISCOVERED || tile.state == RoomState.UNOPENED) &&
            (mapTile is Unknown || mapTile.state == RoomState.UNOPENED || mapTile.state == RoomState.UNDISCOVERED)
        ) return
        val shouldUpdate = mapTile.state.ordinal < tile.state.ordinal
            || (mapTile is RoomTile && tile is RoomTile && mapTile.data.type == RoomType.PUZZLE)
        if (!shouldUpdate) return

        val oldState = tile.state
        tile.state = mapTile.state
        DungeonMap.debug {
            "[HotbarMapScanner] Room state updated at [c=$gridX, r=$gridZ]: $oldState -> ${mapTile.state}"
        }
    }

    private fun updateRoomData(tile: RoomTile, mapTile: RoomTile, gridX: Int, gridZ: Int) {
        if (tile.data.name == "Unknown" && mapTile.data.name != "Unknown") {
            tile.data = mapTile.data
            tile.addToUnique(gridZ, gridX, mapTile.data.name)
            return
        }
        if (mapTile.data.type == tile.data.type || tile.data.name != mapTile.data.name) return

        val oldType = tile.data.type
        tile.data = mapTile.data
        DungeonMap.debug {
            "[HotbarMapScanner] Room data type updated at [c=$gridX, r=$gridZ] for '${tile.data.name}': $oldType -> ${mapTile.data.type}"
        }
    }

    private fun updateDoor(door: DoorTile, mapTile: Tile, gridX: Int, gridZ: Int, level: Level?) {
        if (mapTile is DoorTile && mapTile.type == DoorType.WITHER && door.type != DoorType.WITHER) {
            door.type = mapTile.type
            DungeonMap.debug { "[HotbarMapScanner] Door type updated to WITHER at [c=$gridX, r=$gridZ]" }
        }
        if (door.type !in setOf(DoorType.ENTRANCE, DoorType.WITHER, DoorType.BLOOD)) return

        if (level != null && level.hasChunk(door.x shr 4, door.z shr 4)) {
            val state = level.getBlockState(BlockPos(door.x, 69, door.z))
            val opened = state.isAir || state.`is`(Blocks.BARRIER)
            if (door.opened != opened) {
                door.opened = opened
                DungeonMap.debug {
                    "[HotbarMapScanner] Door opened=$opened at [c=$gridX, r=$gridZ] (wX=${door.x}, wZ=${door.z}) from the world block"
                }
            }
            return
        }

        if (door.opened || mapTile is DoorTile && mapTile.type == DoorType.WITHER) return
        if (mapTile !is DoorTile || mapTile.state != RoomState.DISCOVERED) return
        if (door.type == DoorType.BLOOD && !bloodRoomOpened()) return
        door.opened = true
        DungeonMap.debug { "[HotbarMapScanner] Door opened at [c=$gridX, r=$gridZ] from map tile state" }
    }

    private fun bloodRoomOpened() = MapState.dungeonList.filterIsInstance<RoomTile>()
        .any { it.data.type == RoomType.BLOOD && it.state != RoomState.UNOPENED && it.state != RoomState.UNDISCOVERED }

    fun getTile(arrayX: Int, arrayY: Int): Tile {
        if (arrayX !in MapState.gridRange || arrayY !in MapState.gridRange) return Unknown(0, 0)
        val index = MapState.indexOf(arrayX, arrayY)
        val cached = cachedTiles[index]
        if (cached != null) return cached

        val (xPos, zPos) = MapState.worldAt(arrayX, arrayY)
        val tile = scanTileFromColors(arrayX, arrayY, xPos, zPos)
        cachedTiles[index] = tile
        return tile
    }

    fun getConnected(arrayX: Int, arrayY: Int): List<Pair<Int, Int>> {
        if (getTile(arrayX, arrayY) !is RoomTile) return emptyList()

        val connected = mutableListOf<Pair<Int, Int>>()
        val visited = mutableSetOf(arrayX to arrayY)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(arrayX to arrayY)

        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            if (getTile(cx, cy) !is RoomTile) continue
            connected.add(cx to cy)

            for ((dx, dz) in directions) {
                val nx = cx + dx
                val ny = cy + dz
                if (nx !in MapState.gridRange || ny !in MapState.gridRange) continue
                if (!visited.add(nx to ny)) continue
                queue.add(nx to ny)
            }
        }
        return connected
    }

    private fun scanTileFromColors(arrayX: Int, arrayY: Int, worldX: Int, worldZ: Int): Tile {
        val colorIndex = MapState.indexOf(arrayX, arrayY)
        val centerColor = centerColors[colorIndex].toInt() and 0xFF
        val sideColor = sideColors[colorIndex].toInt() and 0xFF

        if (centerColor == 0) return Unknown(worldX, worldZ)

        return if (arrayX % 2 == 0 && arrayY % 2 == 0) {
            val type = roomTypeOf(sideColor) ?: return Unknown(worldX, worldZ)
            val roomData = when (type) {
                RoomType.BLOOD -> RoomData("Blood", RoomType.BLOOD, RoomShape.S1x1, emptyList(), secrets = 0, crypts = 0)
                RoomType.FAIRY -> RoomData("Fairy", RoomType.FAIRY, RoomShape.S1x1, emptyList(), secrets = 0, crypts = 0)
                RoomType.ENTRANCE -> RoomData("Entrance", RoomType.ENTRANCE, RoomShape.S1x1, emptyList(), secrets = 0, crypts = 0)
                else -> RoomData.createUnknown(type)
            }
            RoomTile(worldX, worldZ, roomData).apply {
                state = when (centerColor) {
                    18 -> when (type) {
                        RoomType.BLOOD -> RoomState.DISCOVERED
                        RoomType.PUZZLE -> RoomState.FAILED
                        else -> state
                    }
                    30 -> when (type) {
                        RoomType.ENTRANCE -> RoomState.DISCOVERED
                        else -> RoomState.GREEN
                    }
                    34 -> RoomState.CLEARED
                    85, 119 -> RoomState.UNOPENED
                    else -> RoomState.DISCOVERED
                }
            }
        } else {
            if (sideColor == 0) {
                val type = doorTypeOf(centerColor) ?: return Unknown(worldX, worldZ)
                DoorTile(worldX, worldZ, type).apply {
                    state = if (centerColor == 85) RoomState.UNOPENED else RoomState.DISCOVERED
                }
            } else {
                val type = roomTypeOf(sideColor) ?: return Unknown(worldX, worldZ)
                RoomTile(worldX, worldZ, RoomData.createUnknown(type)).apply {
                    state = RoomState.DISCOVERED
                    isSeparator = true
                }
            }
        }
    }

    private fun resetColorCaches() {
        centerColors = ByteArray(MapState.GRID_TILE_COUNT)
        sideColors = ByteArray(MapState.GRID_TILE_COUNT)
        cachedTiles = Array(MapState.GRID_TILE_COUNT) { null }
    }
}

// Hypixel's map palette for room sides and door centers.
private fun roomTypeOf(color: Int): RoomType? = when (color) {
    18 -> RoomType.BLOOD
    82 -> RoomType.FAIRY
    34 -> RoomType.RARE
    74 -> RoomType.CHAMPION
    66 -> RoomType.PUZZLE
    62 -> RoomType.TRAP
    63, 85 -> RoomType.NORMAL
    30 -> RoomType.ENTRANCE
    else -> null
}

private fun doorTypeOf(color: Int): DoorType? = when (color) {
    18 -> DoorType.BLOOD
    30 -> DoorType.ENTRANCE
    119 -> DoorType.WITHER
    74, 82, 66, 62, 85, 63 -> DoorType.NORMAL
    else -> null
}
