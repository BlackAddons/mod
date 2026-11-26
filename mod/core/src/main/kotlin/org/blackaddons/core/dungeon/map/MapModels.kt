package org.blackaddons.core.dungeon.map

import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.dungeon.DungeonRoomStateChangeEvent
import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.skyblock.dungeon.DungeonDoor
import org.blackaddons.api.skyblock.dungeon.DungeonRoom
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.api.skyblock.dungeon.RoomShape
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.api.skyblock.dungeon.SecretKind
import org.blackaddons.api.skyblock.dungeon.SecretSpot
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.core.features.impl.dungeons.map.DungeonMap
import java.awt.Color
import kotlin.math.max
import kotlin.time.TimeSource

data class BlockCoord(
    val x: Int = 0,
    val y: Int = 0,
    val z: Int = 0,
)

data class SecretDetails(
    val redstoneKey: Int = 0,
    val wither: Int = 0,
    val bat: Int = 0,
    val item: Int = 0,
    val chest: Int = 0,
)

data class SecretCoords(
    val redstoneKey: List<BlockCoord> = emptyList(),
    val wither: List<BlockCoord> = emptyList(),
    val bat: List<BlockCoord> = emptyList(),
    val item: List<BlockCoord> = emptyList(),
    val chest: List<BlockCoord> = emptyList(),
    val lever: List<BlockCoord> = emptyList(),
) {
    internal fun spots(): List<Pair<SecretKind, BlockCoord>> =
        chest.map { SecretKind.CHEST to it } + item.map { SecretKind.ITEM to it } + bat.map { SecretKind.BAT to it } +
            wither.map { SecretKind.WITHER_ESSENCE to it } + redstoneKey.map { SecretKind.REDSTONE_KEY to it } +
            lever.map { SecretKind.LEVER to it }
}

data class RoomData(
    val name: String,
    val type: RoomType,
    val shape: RoomShape,
    val cores: List<Int>,
    val secretDetails: SecretDetails = SecretDetails(),
    val secretCoords: SecretCoords = SecretCoords(),
    val trappedChests: Int = 0,
    val reviveStones: Int = 0,
    val secrets: Int = 0,
    val crypts: Int = 0,
) {
    val containsPrince: Boolean get() = name in PRINCE_ROOMS

    fun isUnknown() = name == "Unknown" && shape == RoomShape.UNKNOWN

    companion object {
        private val PRINCE_ROOMS = setOf(
            "Big Red Flag", "Chambers", "Doors", "Flags", "Grass Ruin",
            "Leaves", "Market", "Pirate", "Quartz Knight", "Red Blue",
            "Skull", "Sloth", "Supertall", "Waterfall", "Withermancer",
        )

        fun createUnknown(type: RoomType) = RoomData("Unknown", type, RoomShape.UNKNOWN, emptyList())
    }
}

data class MapPlayer(
    val name: String,
    val formattedName: String,
    var icon: String?,
    var uuid: String = "",
    var textures: String = "",
    var mapX: Float = 0f,
    var mapZ: Float = 0f,
    var yaw: Float = 0f,
    var isDead: Boolean = false,
    var targetX: Float = 0f,
    var targetZ: Float = 0f,
    var targetYaw: Float = 0f,
    var lerpStartX: Float = 0f,
    var lerpStartZ: Float = 0f,
    var lerpStartYaw: Float = 0f,
    var lerpStartedAt: TimeSource.Monotonic.ValueTimeMark? = null,
)

interface Tile {
    val x: Int
    val z: Int
    var state: RoomState
    fun getColor(): Color

    fun getGridPos(): Pair<Int, Int> {
        val halfRoom = MapState.ROOM_SIZE shr 1
        val row = (z - MapState.START_Z) / halfRoom
        val column = (x - MapState.START_X) / halfRoom
        return row to column
    }
}

class RoomTile(override val x: Int, override val z: Int, var data: RoomData) : Tile {
    var uniqueRoom: UniqueRoom? = null
    var isSeparator = false

    override var state: RoomState = RoomState.UNDISCOVERED
        set(value) {
            val old = field
            field = value
            val room = uniqueRoom ?: return
            if (room.mainRoom != this) return
            if (old == value) return
            if (data.name == "Unknown") return
            if (value == RoomState.GREEN) room.foundSecrets = room.data.secrets
            DungeonMap.debug {
                "[DungeonMap] Room state change for '${data.name}': $old -> $value"
            }
            EventBus.post(DungeonRoomStateChangeEvent(data.name, old, value))
        }

    override fun getColor(): Color = when {
        !MapOverrides.showsUnrevealed && (state == RoomState.UNOPENED || state == RoomState.UNDISCOVERED) -> DungeonMap.colorUnopened.value
        data.type == RoomType.BLOOD -> DungeonMap.colorBlood.value
        data.type == RoomType.FAIRY -> DungeonMap.colorFairy.value
        data.type == RoomType.RARE -> DungeonMap.colorRare.value
        data.type == RoomType.CHAMPION -> DungeonMap.colorMiniboss.value
        data.type == RoomType.PUZZLE -> DungeonMap.colorPuzzle.value
        data.type == RoomType.TRAP -> DungeonMap.colorTrap.value
        data.type == RoomType.ENTRANCE -> DungeonMap.colorEntrance.value
        else -> DungeonMap.colorRoom.value
    }

    fun addToUnique(row: Int, column: Int, roomName: String = data.name) {
        val unique = MapState.uniqueRooms[roomName]
        if (unique == null) {
            UniqueRoom(column, row, this).also {
                MapState.uniqueRooms[roomName] = it
                uniqueRoom = it
                DungeonMap.debug {
                    "[DungeonMap] Created UniqueRoom '$roomName' (shape=${data.shape}, type=${data.type}) at grid [c=$column, r=$row]"
                }
            }
        } else {
            unique.addTile(column, row, this)
            uniqueRoom = unique
            DungeonMap.debug {
                "[DungeonMap] Added tile to UniqueRoom '$roomName' at grid [c=$column, r=$row] (total tiles: ${unique.tiles.size})"
            }
        }
    }
}

class DoorTile(override val x: Int, override val z: Int, override var type: DoorType) : Tile, DungeonDoor {
    override var state = RoomState.UNDISCOVERED
    override var opened = false

    override fun getColor(): Color = when {
        !MapOverrides.showsUnrevealed && (state == RoomState.UNOPENED || state == RoomState.UNDISCOVERED) -> DungeonMap.colorUnopenedDoor.value
        type == DoorType.BLOOD -> DungeonMap.colorBloodDoor.value
        type == DoorType.ENTRANCE -> DungeonMap.colorEntranceDoor.value
        type == DoorType.WITHER || DungeonTree.isFairy(this) -> DungeonMap.colorWitherDoor.value
        else -> {
            val coloredRooms = roomTiles.filter { it.data.type != RoomType.NORMAL }
            val roomTile = if (coloredRooms.size == 2) coloredRooms.find { it.data.type != RoomType.FAIRY }
            else coloredRooms.firstOrNull()
            roomTile?.getColor() ?: DungeonMap.colorRoomDoor.value
        }
    }

    val roomTiles: List<RoomTile>
        get() {
            val (row, column) = getGridPos()
            return MapState.adjacentRoomTiles(column, row).toList().filterNotNull()
        }
}

class Unknown(override val x: Int, override val z: Int) : Tile {
    override var state: RoomState = RoomState.UNDISCOVERED
    override fun getColor(): Color = Color(0, 0, 0, 0)
}

class UniqueRoom(arrX: Int, arrY: Int, roomTile: RoomTile) : DungeonRoom {
    var topLeft = arrX to arrY
    var center = arrX to arrY
    val tiles = mutableListOf(roomTile)

    var mainRoom: RoomTile = roomTile
    val data: RoomData get() = mainRoom.data
    override val name: String get() = data.name
    override val type: RoomType get() = data.type
    override val shape: RoomShape get() = data.shape
    override val state: RoomState get() = mainRoom.state
    override val secrets: Int get() = data.secrets
    override val crypts: Int get() = data.crypts
    val cacheSplitName: List<String> get() = name.split(" ")

    var highestBlock: Int? = null
    var clayPos: Triple<Int, Int, Int>? = null
    var rotation: Int? = null

    override var hasMimic = false
    override var mimicChests: List<BlockPos3> = emptyList()

    /** Known once the clay anchor and the rotation are scanned. */
    internal val transform: RoomTransform?
        get() {
            val clay = clayPos ?: return null
            return RoomTransform(clay.first, clay.third, rotation ?: return null)
        }

    override fun toWorld(local: BlockPos3): BlockPos3? = transform?.toWorld(local.x, local.z)?.let { (x, z) -> BlockPos3(x, local.y, z) }

    override fun toLocal(world: BlockPos3): BlockPos3? = transform?.toLocal(world.x, world.z)?.let { (x, z) -> BlockPos3(x, world.y, z) }

    override val secretSpots: List<SecretSpot>
        get() {
            val transform = transform ?: return emptyList()
            if (transform != spotsFor) {
                spotsFor = transform
                spots = data.secretCoords.spots().map { (kind, local) ->
                    val (x, z) = transform.toWorld(local.x, local.z)
                    SecretSpot(kind, BlockPos3(x, local.y, z))
                }
            }
            return spots
        }
    private var spotsFor: RoomTransform? = null
    private var spots: List<SecretSpot> = emptyList()
    override var foundSecrets = 0
    override val isFullyCleared: Boolean
        get() = when {
            data.type == RoomType.BLOOD -> mainRoom.state == RoomState.GREEN
            else -> mainRoom.state == RoomState.GREEN || (mainRoom.state == RoomState.CLEARED && foundSecrets >= data.secrets)
        }
    private var cachedMapCells: List<Pair<Int, Int>>? = null

    init {
        roomTile.uniqueRoom = this
        MapState.cryptCount += roomTile.data.crypts
        MapState.secretCount += roomTile.data.secrets
    }

    private var boundsDirty = true
    var cachedMaxWidth = 0f
    var cachedMaxHeight = 0f

    fun updateBounds(roomSize: Float, gapSize: Float) {
        if (!boundsDirty) return
        boundsDirty = false

        var maxWidth = roomSize
        var maxHeight = roomSize

        if (data.shape != RoomShape.SL && data.shape != RoomShape.S1x1 && data.shape != RoomShape.S2x2) {
            var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
            var minZ = Int.MAX_VALUE; var maxZ = Int.MIN_VALUE
            for (tile in tiles) {
                if (tile.x < minX) minX = tile.x
                if (tile.x > maxX) maxX = tile.x
                if (tile.z < minZ) minZ = tile.z
                if (tile.z > maxZ) maxZ = tile.z
            }
            val tilesWide = (maxX - minX) / 32 + 1
            val tilesTall = (maxZ - minZ) / 32 + 1
            maxWidth = (tilesWide * roomSize) + (max(0, tilesWide - 1) * gapSize)
            maxHeight = (tilesTall * roomSize) + (max(0, tilesTall - 1) * gapSize)
        }
        if (data.shape == RoomShape.SL) {
            maxWidth = roomSize * 2 + gapSize
            // Keep L-room labels on the two-cell arm.
            maxHeight = roomSize
        }
        if (data.shape == RoomShape.S2x2) {
            maxWidth = roomSize * 2 + gapSize
            maxHeight = roomSize * 2 + gapSize
        }

        cachedMaxWidth = maxWidth
        cachedMaxHeight = maxHeight
    }

    fun mapCells(): List<Pair<Int, Int>> = cachedMapCells ?: tiles.asSequence()
        .filterNot { it.isSeparator }
        .map { tile ->
            val (row, column) = tile.getGridPos()
            (column shr 1) to (row shr 1)
        }
        .distinct()
        .toList()
        .also { cachedMapCells = it }

    fun addTile(x: Int, y: Int, tile: RoomTile) {
        boundsDirty = true
        cachedMapCells = null
        tiles.removeIf { it.x == tile.x && it.z == tile.z }
        tiles.add(tile)
        tile.uniqueRoom = this

        if (!tile.isSeparator && (x < topLeft.first || (x == topLeft.first && y <= topLeft.second))) {
            topLeft = x to y
            mainRoom = tile
        }

        if (tiles.size == 1) {
            center = x to y
            return
        }

        val roomTiles = tiles.mapNotNull { t ->
            if (t.isSeparator) null else t.getGridPos()
        }.ifEmpty { return }

        val xRooms = roomTiles.groupBy { it.second }.entries.sortedByDescending { it.value.size }
        val zRooms = roomTiles.groupBy { it.first }.entries.sortedByDescending { it.value.size }

        center = when {
            zRooms.size == 1 || zRooms[0].value.size != zRooms[1].value.size ->
                xRooms.sumOf { it.key } / xRooms.size to zRooms[0].key
            xRooms.size == 1 || xRooms[0].value.size != xRooms[1].value.size ->
                xRooms[0].key to zRooms.sumOf { it.key } / zRooms.size
            else ->
                (xRooms[0].key + xRooms[1].key) / 2 to (zRooms[0].key + zRooms[1].key) / 2
        }
    }

    fun getCheckmarkPosition(centered: Boolean) = if (centered) center else topLeft
}
