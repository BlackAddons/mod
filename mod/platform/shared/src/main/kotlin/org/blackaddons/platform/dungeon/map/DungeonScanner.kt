package org.blackaddons.platform.dungeon.map

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.GlazedTerracottaBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.dungeon.DungeonTileScannedEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.skyblock.dungeon.RoomShape
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.core.dungeon.ScoreCalculation
import org.blackaddons.core.dungeon.map.DoorTile
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.RoomHashCollection
import org.blackaddons.core.dungeon.map.RoomRegistry
import org.blackaddons.core.dungeon.map.RoomTile
import org.blackaddons.core.dungeon.map.Tile
import org.blackaddons.core.dungeon.map.UniqueRoom
import org.blackaddons.core.dungeon.map.Unknown
import org.blackaddons.core.features.impl.dungeons.map.DungeonMap
import org.blackaddons.platform.compat.isBloodDoorWool
import org.blackaddons.platform.compat.isBlueTerracotta
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Scans dungeon blocks every 250ms to find rooms and doors for [MapState]. */
object DungeonScanner {
    private val SCAN_INTERVAL = 250.milliseconds
    private const val DOOR_FLOOR_Y = 68
    private const val DOOR_BLOCK_Y = 69
    private const val BOBBY_FAKE_CHUNK = "de.johni0702.minecraft.bobby.FakeChunk"

    private val bobbyManagesWorlds by lazy {
        runCatching {
            if (!FabricLoader.getInstance().isModLoaded("bobby")) return@runCatching false
            val bobby = Class.forName("de.johni0702.minecraft.bobby.Bobby")
                .getMethod("getInstance")
                .invoke(null)
            val config = bobby.javaClass.getMethod("getConfig").invoke(bobby)
            config.javaClass.getMethod("isDynamicMultiWorld").invoke(config) as Boolean
        }.getOrDefault(false)
    }

    private var lastScanTime: TimeSource.Monotonic.ValueTimeMark? = null
    private val loggedUnknownCores = mutableSetOf<Int>()
    private val reportedUnknownTiles = mutableSetOf<Pair<Int, Int>>()

    private val clayBlockCorners = listOf(
        -MapState.HALF_ROOM_SIZE to -MapState.HALF_ROOM_SIZE,
        MapState.HALF_ROOM_SIZE to -MapState.HALF_ROOM_SIZE,
        MapState.HALF_ROOM_SIZE to MapState.HALF_ROOM_SIZE,
        -MapState.HALF_ROOM_SIZE to MapState.HALF_ROOM_SIZE,
    )

    private val doorwayRoofHeights = setOf(73, 74, 81, 82)

    fun init() {
        EventBus.subscribe<WorldChangeEvent> {
            lastScanTime = null
            loggedUnknownCores.clear()
            reportedUnknownTiles.clear()
            MapState.reset()
        }

        EventBus.subscribe<ClientTickEvent.Post> {
            if (!LocationUtils.inDungeonRooms) return@subscribe
            val now = TimeSource.Monotonic.markNow()
            if (lastScanTime?.let { now - it < SCAN_INTERVAL } == true) return@subscribe
            scan(now)
            val floor = LocationUtils.dungeonFloorNumber ?: return@subscribe
            if (floor < 6) return@subscribe
            if (ScoreCalculation.mimicKilled) return@subscribe
            if (MapState.mimicRoom != null) return@subscribe
            findMimicRoom()
        }
    }

    private fun scan(now: TimeSource.Monotonic.ValueTimeMark) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        if (!MapState.calibrated) {
            LocationUtils.dungeonFloorNumber?.let { floor ->
                MapState.applyCalibration(MapState.calibrationFor(floor))
            }
        }

        for (x in MapState.gridRange) for (z in MapState.gridRange) {
            val (wX, wZ) = MapState.worldAt(x, z)

            val chunk = level.getChunk(wX shr 4, wZ shr 4, ChunkStatus.FULL, false) ?: continue
            if (chunk.javaClass.name == BOBBY_FAKE_CHUNK && !bobbyManagesWorlds) continue

            val inGrid = MapState.tileAt(x, z) ?: continue
            if (inGrid !is Unknown && (inGrid as? RoomTile)?.data?.isUnknown() != true) continue
            val roofHeight = getHighestY(level, wX, wZ)
            if (roofHeight <= 0) continue

            val tile = scanTile(
                level = level,
                worldX = wX,
                worldZ = wZ,
                gridRow = z,
                gridColumn = x,
                roofHeight = roofHeight,
            ) ?: continue
            if (inGrid !is Unknown) tile.state = inGrid.state
            MapState.replaceTile(x, z, tile)
        }

        lastScanTime = now
        for (unq in MapState.uniqueRooms.values) {
            findRotation(level, unq)
        }
    }

    private fun scanTile(
        level: Level,
        worldX: Int,
        worldZ: Int,
        gridRow: Int,
        gridColumn: Int,
        roofHeight: Int,
    ): Tile? {
        val rowEven = gridRow and 1 == 0
        val columnEven = gridColumn and 1 == 0

        return when {
            // Room center
            rowEven && columnEven -> {
                val tilePosition = worldX to worldZ
                if (tilePosition in reportedUnknownTiles) return null
                val roomHash = ModernRoomHasher.hash(level, worldX, worldZ)
                val roomData = RoomRegistry.getRoomData(roomHash)
                if (roomData == null) {
                    val roomCore = ModernRoomHasher.sample(level, worldX, worldZ)
                    reportedUnknownTiles.add(tilePosition)
                    if (DungeonMap.collectUnknownRoomData.value) {
                        RoomHashCollection.recordIfNew(roomCore, Platform.minecraftVersion)
                        if (DungeonMap.collectUnknownRoomSnapshots.value) {
                            RoomSnapshotCollector.queueUnknown(roomCore, worldX, worldZ)
                        }
                    }
                    if (loggedUnknownCores.add(roomHash)) {
                        DungeonMap.debug {
                            "[DungeonScanner] Room center at [c=$gridColumn, r=$gridRow] (wX=$worldX, wZ=$worldZ): nativeCore=$roomHash NOT found in RoomRegistry (roofHeight=$roofHeight)"
                        }
                    }
                    return null
                }
                DungeonMap.debug {
                    "[DungeonScanner] Room center identified at [c=$gridColumn, r=$gridRow] (wX=$worldX, wZ=$worldZ): '${roomData.name}' (${roomData.shape}, ${roomData.type}, nativeCore=$roomHash, roofHeight=$roofHeight)"
                }
                RoomTile(worldX, worldZ, roomData).apply {
                    addToUnique(gridRow, gridColumn)
                    uniqueRoom?.highestBlock = roofHeight
                }
            }

            // Center of a 2x2, only if the neighbor really is one.
            !rowEven && !columnEven -> {
                val neighbor = MapState.tileAt(gridColumn - 1, gridRow - 1) as? RoomTile
                if (neighbor != null && neighbor.data.shape == RoomShape.S2x2) {
                    DungeonMap.debug {
                        "[DungeonScanner] 2x2 center separator at [c=$gridColumn, r=$gridRow] (wX=$worldX, wZ=$worldZ) for '${neighbor.data.name}'"
                    }
                    RoomTile(worldX, worldZ, neighbor.data).apply {
                        isSeparator = true
                        addToUnique(gridRow, gridColumn, neighbor.data.name)
                    }
                } else null
            }

            // Doorway between rooms
            roofHeight in doorwayRoofHeights -> {
                scanDoorway(level, worldX, worldZ, gridRow, gridColumn, roofHeight)
            }

            // Multi-tile room connection or entrance door.
            else -> {
                val (prevNeighbor, nextNeighbor) = MapState.adjacentRoomTiles(gridColumn, gridRow)

                when {
                    prevNeighbor?.data?.type == RoomType.ENTRANCE || nextNeighbor?.data?.type == RoomType.ENTRANCE -> {
                        DungeonMap.debug {
                            "[DungeonScanner] Entrance doorway detected at [c=$gridColumn, r=$gridRow] (wX=$worldX, wZ=$worldZ)"
                        }
                        DoorTile(worldX, worldZ, DoorType.ENTRANCE)
                    }
                    prevNeighbor == null -> null
                    prevNeighbor.data.shape == RoomShape.S1x1 || prevNeighbor.data.shape == RoomShape.UNKNOWN -> null
                    else -> {
                        DungeonMap.debug {
                            "[DungeonScanner] Multi-tile separator at [c=$gridColumn, r=$gridRow] (wX=$worldX, wZ=$worldZ) for '${prevNeighbor.data.name}' (${prevNeighbor.data.shape})"
                        }
                        RoomTile(worldX, worldZ, prevNeighbor.data).apply {
                            isSeparator = true
                            addToUnique(gridRow, gridColumn, prevNeighbor.data.name)
                        }
                    }
                }
            }
        }
    }

    private fun findMimicRoom() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        val roomChests = mutableMapOf<UniqueRoom, MutableList<BlockPos3>>()
        val renderDist = mc.options.renderDistance().get()
        val pX = player.chunkPosition().x
        val pZ = player.chunkPosition().z

        val minChunkX = maxOf(-13, pX - renderDist)
        val maxChunkX = minOf(2, pX + renderDist)
        val minChunkZ = maxOf(-13, pZ - renderDist)
        val maxChunkZ = minOf(2, pZ + renderDist)

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false) ?: continue
                for (pos in chunk.blockEntitiesPos) {
                    if (!level.getBlockState(pos).`is`(Blocks.TRAPPED_CHEST)) continue

                    val room = MapState.getRoomAt(pos.x.toDouble(), pos.z.toDouble()) ?: continue
                    val chests = roomChests.getOrPut(room) { mutableListOf() }
                    chests.add(BlockPos3(pos.x, pos.y, pos.z))

                    if (chests.size > room.data.trappedChests) {
                        room.mimicChests = chests.toList()
                        room.hasMimic = true
                        MapState.mimicRoom = room
                        DungeonMap.debug { "[DungeonScanner] Mimic found in room ${room.data.name} with ${chests.size} chests" }
                    }
                }
            }
        }
    }

    private fun findRotation(level: Level, room: UniqueRoom) {
        if (room.rotation != null || room.clayPos != null) return
        if (room.mainRoom.data.type == RoomType.FAIRY) {
            room.clayPos = Triple(room.mainRoom.x - 15, 0, room.mainRoom.z - 15)
            room.rotation = 0
            val (row, column) = room.mainRoom.getGridPos()
            EventBus.post(DungeonTileScannedEvent(column, row))
            return
        }
        val roomTiles = room.tiles.filterNot { it.isSeparator }
        if (roomTiles.size < room.data.shape.tileCount) return
        val highestBlock = room.highestBlock
            ?: getHighestY(level, room.mainRoom.x, room.mainRoom.z)
                .takeIf { it > 0 }
                ?.also { room.highestBlock = it }
            ?: return

        val mutablePos = BlockPos.MutableBlockPos()
        val h = MapState.HALF_ROOM_SIZE

        val minX = roomTiles.minOf { it.x }
        val maxX = roomTiles.maxOf { it.x }
        val minZ = roomTiles.minOf { it.z }
        val maxZ = roomTiles.maxOf { it.z }

        val primaryCorners = listOf(
            minX - h to minZ - h,
            maxX + h to minZ - h,
            maxX + h to maxZ + h,
            minX - h to maxZ + h,
        )

        if (room.data.shape != RoomShape.SL) {
            for ((index, corner) in primaryCorners.withIndex()) {
                mutablePos.set(corner.first, highestBlock, corner.second)
                if (!level.hasChunk(mutablePos.x shr 4, mutablePos.z shr 4)) continue
                if (applyRotationAt(level, room, mutablePos, index * 90)) return
            }
        } else {
            for (tile in roomTiles) for ((index, corner) in clayBlockCorners.withIndex()) {
                val (offsetX, offsetZ) = corner
                mutablePos.set(tile.x + offsetX, highestBlock, tile.z + offsetZ)
                if (!level.hasChunk(mutablePos.x shr 4, mutablePos.z shr 4)) continue
                if (applyRotationAt(level, room, mutablePos, index * 90)) return
            }
        }
    }

    private fun applyRotationAt(
        level: Level,
        room: UniqueRoom,
        position: BlockPos,
        rotation: Int,
    ): Boolean {
        if (!level.getBlockState(position).isBlueTerracotta()) return false
        room.clayPos = Triple(position.x, 0, position.z)
        room.rotation = rotation
        val (row, column) = room.mainRoom.getGridPos()
        EventBus.post(DungeonTileScannedEvent(column, row))
        DungeonMap.debug {
            "[DungeonScanner] Rotation found for '${room.name}': rotation=$rotation deg, clayPos=(${position.x}, ${position.z})"
        }
        return true
    }

    private fun getHighestY(level: Level, x: Int, z: Int): Int {
        val pos = BlockPos.MutableBlockPos(x, 0, z)
        for (y in 256 downTo 0) {
            val state = level.getBlockState(pos.setY(y))
            if (state.isAir || state.`is`(Blocks.GOLD_BLOCK)) continue
            return y
        }
        return 0
    }

    private fun scanDoorway(
        level: Level, x: Int, z: Int, row: Int, column: Int, roofHeight: Int,
    ): DoorTile? {
        val floorState = level.getBlockState(BlockPos(x, DOOR_FLOOR_Y, z))
        if (floorState.isAir) return null

        val doorPos = BlockPos(x, DOOR_BLOCK_Y, z)
        val doorState = level.getBlockState(doorPos)
        val rowEven = row and 1 == 0
        val (prevNeighbor, nextNeighbor) = MapState.adjacentRoomTiles(column, row)
        val isEntranceDoor = prevNeighbor?.data?.type == RoomType.ENTRANCE || nextNeighbor?.data?.type == RoomType.ENTRANCE

        val type = when {
            isEntranceDoor -> DoorType.ENTRANCE
            doorState.`is`(Blocks.COAL_BLOCK) -> DoorType.WITHER
            isBloodDoorBlock(doorState) -> DoorType.BLOOD
            doorState.isAir || doorState.`is`(Blocks.BARRIER) -> DoorType.NORMAL
            else -> return null
        }

        if (rowEven) {
            val s1 = level.getBlockState(BlockPos(x - 1, DOOR_BLOCK_Y, z))
            val s2 = level.getBlockState(BlockPos(x + 1, DOOR_BLOCK_Y, z))
            if (isDoorwayWall(s1) || isDoorwayWall(s2)) return null
        } else {
            val s1 = level.getBlockState(BlockPos(x, DOOR_BLOCK_Y, z - 1))
            val s2 = level.getBlockState(BlockPos(x, DOOR_BLOCK_Y, z + 1))
            if (isDoorwayWall(s1) || isDoorwayWall(s2)) return null
        }

        if (type == DoorType.WITHER) MapState.witherDoors++
        DungeonMap.debug {
            "[DungeonScanner] Doorway detected at [c=$column, r=$row] (wX=$x, wZ=$z): type=$type, roofHeight=$roofHeight"
        }
        return DoorTile(x, z, type)
    }

    private fun isBloodDoorBlock(state: BlockState): Boolean =
        (state.`is`(BlockTags.TERRACOTTA) && !state.`is`(Blocks.TERRACOTTA)) ||
            state.block is GlazedTerracottaBlock || state.isBloodDoorWool()

    private fun isDoorwayWall(state: BlockState): Boolean = state.block in doorwayWallBlocks

    // Stone tags include decorative doorway blocks, so this palette stays explicit.
    private val doorwayWallBlocks = setOf(
        Blocks.STONE,
        Blocks.COBBLESTONE,
        Blocks.MOSSY_COBBLESTONE,
        Blocks.ANDESITE,
        Blocks.DIORITE,
        Blocks.GRANITE,
        Blocks.POLISHED_ANDESITE,
        Blocks.POLISHED_DIORITE,
        Blocks.POLISHED_GRANITE,
        Blocks.BEDROCK,
        Blocks.STONE_BRICKS,
        Blocks.MOSSY_STONE_BRICKS,
        Blocks.CRACKED_STONE_BRICKS,
        Blocks.CHISELED_STONE_BRICKS,
        Blocks.STONE_BRICK_STAIRS,
        Blocks.COBBLESTONE_STAIRS,
    )
}
