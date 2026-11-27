package org.blackaddons.core.features.impl.visual.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.player.BlockFace
import org.blackaddons.api.player.CameraRay
import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.api.util.nearZero
import org.blackaddons.api.util.packInts
import org.blackaddons.api.util.packedHigh
import org.blackaddons.api.util.packedLow
import org.blackaddons.api.world.BlockAppearance
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.UniqueRoom
import kotlin.math.abs

internal data class FmeSnapshot(val replacements: Map<FmeRuleKey, FmePositionRule>, val typeReplacements: List<String>)

internal data class FmeActiveContext(
    val location: String?,
    val inDungeon: Boolean,
)

internal data class FmeWorldReplacement(val pos: BlockPos3, val blockId: String)

internal data class FmeRenderPosition(
    val sourceBlockId: String,
    val ghost: Boolean,
    val appearance: BlockAppearance,
) {
    fun appliesTo(blockId: String, isAir: Boolean): Boolean =
        (!isAir || ghost) && (sourceBlockId == FME_ANY_SOURCE || sourceBlockId == blockId)
}

internal data class FmeRenderIndex(
    val positions: FmePositionIndex,
    val globalTypes: Map<String, BlockAppearance>,
    val locationTypes: Map<String, BlockAppearance>,
    val roomTypes: Map<Long, Map<String, BlockAppearance>>,
    val ghosts: List<FmeWorldReplacement> = emptyList(),
    val phaseTypes: List<Map<String, BlockAppearance>> = emptyList(),
) {
    val active: Boolean
        get() = !positions.isEmpty() || globalTypes.isNotEmpty() || locationTypes.isNotEmpty() ||
            roomTypes.isNotEmpty() || phaseTypes.any(Map<String, BlockAppearance>::isNotEmpty)

    fun locationTypesAt(y: Int): Map<String, BlockAppearance> =
        if (phaseTypes.isEmpty()) locationTypes else phaseTypes.getOrNull(DungeonGeometry.f7Phase(y.toDouble()).ordinal).orEmpty()

    internal val typesByPhase: List<Map<String, BlockAppearance>>
        get() = phaseTypes.ifEmpty { List(F7_PHASES.count()) { locationTypes } }

    /** Lets block entities skip tint lookups per position when nothing tints. */
    val tinted: Boolean =
        positions.palette.any { it.appearance.tintRgb != UNTINTED } ||
            globalTypes.values.any { it.tintRgb != UNTINTED } ||
            locationTypes.values.any { it.tintRgb != UNTINTED } ||
            phaseTypes.any { phase -> phase.values.any { it.tintRgb != UNTINTED } } ||
            roomTypes.values.any { room -> room.values.any { it.tintRgb != UNTINTED } }

    fun lookupKind(x: Int, y: Int, z: Int): Int {
        if (!active) return 0
        if (globalTypes.isNotEmpty() || locationTypes.isNotEmpty() || phaseTypes.any(Map<String, BlockAppearance>::isNotEmpty)) return 2
        if (positions[x, y, z] != null) return 1
        return if (roomTypes.containsKey(packFmeRoomCell(x, z))) 2 else 0
    }

    companion object {
        const val UNTINTED = BlockAppearance.UNTINTED
        val EMPTY = FmeRenderIndex(FmePositionIndex.EMPTY, emptyMap(), emptyMap(), emptyMap(), emptyList())
    }
}

internal class FmePositionIndex private constructor(
    private val sections: Map<Long, CharArray>,
    val palette: List<FmeRenderPosition>,
) {
    fun isEmpty(): Boolean = sections.isEmpty()

    operator fun get(x: Int, y: Int, z: Int): FmeRenderPosition? {
        val section = sections[FmeCells.sectionKey(x shr 4, y shr 4, z shr 4)] ?: return null
        val slot = section[FmeCells.cellIndex(x, y, z)].code
        return if (slot == 0) null else palette[slot - 1]
    }

    fun changesFrom(previous: FmePositionIndex): Pair<Set<Pair<Int, Int>>, Set<BlockPos3>> {
        val slots = palette.withIndex().associate { (index, position) -> position to index + 1 }
        val remap = IntArray(previous.palette.size + 1) { slot -> if (slot == 0) 0 else slots[previous.palette[slot - 1]] ?: -1 }
        val chunks = linkedSetOf<Pair<Int, Int>>()
        val blocks = linkedSetOf<BlockPos3>()
        (sections.keys + previous.sections.keys).forEach { key ->
            val current = sections[key]
            val before = previous.sections[key]
            val changed = ArrayList<Int>()
            for (cell in 0 until FmeCells.SECTION_CELLS) {
                val now = current?.get(cell)?.code ?: 0
                val was = before?.get(cell)?.code ?: 0
                if (remap[was] != now) changed += cell
            }
            if (changed.isEmpty()) return@forEach
            val sx = FmeCells.sectionX(key)
            val sz = FmeCells.sectionZ(key)
            if (changed.size > COLUMN_REDRAW_CELLS) {
                chunks += sx to sz
                return@forEach
            }
            val sy = FmeCells.sectionY(key)
            changed.mapTo(blocks) { cell -> BlockPos3((sx shl 4) + (cell and 15), (sy shl 4) + (cell shr 8), (sz shl 4) + (cell shr 4 and 15)) }
        }
        return chunks to blocks
    }

    class Builder {
        private val sections = HashMap<Long, CharArray>()
        private val slots = HashMap<FmeRenderPosition, Char>()
        private val palette = ArrayList<FmeRenderPosition>()

        fun set(x: Int, y: Int, z: Int, position: FmeRenderPosition) {
            val slot = slotOf(position) ?: return
            section(x, y, z)[FmeCells.cellIndex(x, y, z)] = slot
        }

        fun fill(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int, position: FmeRenderPosition) {
            val slot = slotOf(position) ?: return
            val section = section(minX, minY, minZ)
            for (y in minY..maxY) for (z in minZ..maxZ) {
                val row = FmeCells.cellIndex(0, y, z)
                section.fill(slot, row + (minX and 15), row + (maxX and 15) + 1)
            }
        }

        fun build(): FmePositionIndex = if (sections.isEmpty()) EMPTY else FmePositionIndex(sections, palette)

        private fun section(x: Int, y: Int, z: Int): CharArray =
            sections.getOrPut(FmeCells.sectionKey(x shr 4, y shr 4, z shr 4)) { CharArray(FmeCells.SECTION_CELLS) }

        private fun slotOf(position: FmeRenderPosition): Char? = slots[position] ?: run {
            if (palette.size >= Char.MAX_VALUE.code) return null
            palette += position
            Char(palette.size).also { slots[position] = it }
        }
    }

    companion object {
        val EMPTY = FmePositionIndex(emptyMap(), emptyList())

        private const val COLUMN_REDRAW_CELLS = 64
    }
}

/** Source ids whose appearance changed everywhere. */
internal data class FmeRenderInvalidation(
    val blockTypes: Set<String> = emptySet(),
    val chunks: Set<Pair<Int, Int>> = emptySet(),
    val blocks: Set<BlockPos3> = emptySet(),
)

internal fun FmeRenderIndex.invalidationFrom(previous: FmeRenderIndex): FmeRenderInvalidation {
    val blockTypes = changedKeys(globalTypes, previous.globalTypes) +
        changedPhaseKeys(typesByPhase, previous.typesByPhase)
    val changedRoomCells = changedKeys(roomTypes, previous.roomTypes)
    val (positionChunks, positionBlocks) = positions.changesFrom(previous.positions)
    val chunks = changedRoomCells.flatMapTo(linkedSetOf(), ::roomCellChunks).apply { addAll(positionChunks) }
    val blocks = positionBlocks.filterNotTo(linkedSetOf()) { ((it.x shr 4) to (it.z shr 4)) in chunks }
    return FmeRenderInvalidation(blockTypes, chunks, blocks)
}

private fun <K> changedPhaseKeys(current: List<Map<K, *>>, previous: List<Map<K, *>>): Set<K> =
    (current + previous).flatMapTo(linkedSetOf()) { it.keys }.filterTo(linkedSetOf()) { key ->
        current.map { it[key] } != previous.map { it[key] }
    }

private fun <K> changedKeys(current: Map<K, *>, previous: Map<K, *>): Set<K> =
    (current.keys + previous.keys).filterTo(linkedSetOf()) { current[it] != previous[it] }

internal fun buildFmeRenderIndex(
    profileId: String,
    replacements: FmeReplacementStore,
    typeReplacements: FmeTypeReplacementStore,
    context: FmeActiveContext,
): FmeRenderIndex {
    val globalTypes = typeReplacements.entries(profileId, FmeScope.GLOBAL)
        .filter(FmeTypeReplacement::enabled)
        .toAppearanceMap()
    fun locationTypes(location: String) = typeReplacements.entries(profileId, FmeScope.LOCATION)
        .filter { it.scopeKey == location && it.enabled }
        .toAppearanceMap()
    val locationTypes = context.location?.let(::locationTypes).orEmpty()
    val phaseKeys = F7_PHASES.map { fmeBossLocationKey(7, it) }
    val phaseTypes = if (context.location in phaseKeys) phaseKeys.map(::locationTypes) else emptyList()
    val positions = FmePositionIndex.Builder()
    val ghosts = mutableListOf<FmeWorldReplacement>()

    context.location?.let { location ->
        val drawn = fmePositionLocations(location)
        val rules = replacements.rules(profileId, FmeScope.LOCATION).filter { it.key.scopeKey in drawn && it.key.enabled }
        for (ownHeight in listOf(false, true)) rules.forEach { rule ->
            val render = rule.key.renderPosition(quarterTurns = 0)
            rule.cells.forEachBox { minX, minY, minZ, maxX, maxY, maxZ ->
                for (y in minY..maxY) {
                    if (fmeOwnsHeight(rule.key.scopeKey, y) == ownHeight) positions.fill(minX, y, minZ, maxX, y, maxZ, render)
                }
            }
        }
        rules.filter { it.key.targetable }.forEach { rule ->
            rule.cells.forEach { x, y, z -> ghosts += FmeWorldReplacement(BlockPos3(x, y, z), rule.key.blockId) }
        }
    }

    val roomTypes = HashMap<Long, Map<String, BlockAppearance>>()
    if (context.inDungeon) {
        val rooms = MapState.uniqueRooms.values.toList()
        val rulesByRoom = typeReplacements.entries(profileId, FmeScope.DUNGEON)
            .filter(FmeTypeReplacement::enabled)
            .groupBy(FmeTypeReplacement::scopeKey)
            .mapValues { (_, rules) -> rules.toAppearanceMap() }
        rooms.forEach { room ->
            rulesByRoom[room.name]?.takeIf(Map<String, BlockAppearance>::isNotEmpty)?.let { rules ->
                room.tiles.filterNot { it.isSeparator }.forEach { tile -> roomTypes[packFmeRoomCell(tile.x, tile.z)] = rules }
            }
        }
        replacements.rules(profileId, FmeScope.DUNGEON).filter { it.key.enabled }
            .groupBy { it.key.scopeKey }
            .forEach { (roomName, roomRules) ->
                rooms.filter { it.name == roomName }.forEach roomLoop@{ room ->
                    val transform = room.transform ?: return@roomLoop
                    roomRules.forEach { rule ->
                        val render = rule.key.renderPosition(transform.quarterTurns)
                        rule.cells.forEach { x, y, z ->
                            val (worldX, worldZ) = transform.toWorld(x, z)
                            positions.set(worldX, y, worldZ, render)
                            if (rule.key.targetable) ghosts += FmeWorldReplacement(BlockPos3(worldX, y, worldZ), rule.key.blockId)
                        }
                    }
                }
            }
    }
    return FmeRenderIndex(positions.build(), globalTypes, locationTypes, roomTypes, ghosts, phaseTypes)
}

/**
 * The grid gives the Entrance's doorway gap to a neighbor and its back to an unmarked cell. FME keeps both as part of
 * the Entrance.
 */
internal fun fmeRoomAt(x: Int, z: Int): UniqueRoom? {
    val room = MapState.getRoomAt(x.toDouble(), z.toDouble())
    val entrance = MapState.uniqueRooms.values.firstOrNull { it.type == RoomType.ENTRANCE }
    if (entrance == null || room === entrance) return room
    val cell = packFmeRoomCell(x, z)
    val owned = entrance.tiles.filterNot { it.isSeparator }.any { tile ->
        val inGap = abs(x - tile.x) <= ENTRANCE_REACH && abs(z - tile.z) <= ENTRANCE_REACH
        val tileCell = packFmeRoomCell(tile.x, tile.z)
        val behind = room == null &&
            abs(cell.packedHigh() - tileCell.packedHigh()) + abs(cell.packedLow() - tileCell.packedLow()) == 1
        inGap || behind
    }
    return if (owned) entrance else room
}

private const val ENTRANCE_REACH = MapState.HALF_ROOM_SIZE + 1

internal fun FmeReplacement.managedGroup() = FmeManagedGroup(
    kind = FmeManagedKind.POSITION,
    scope = scope,
    scopeKey = scopeKey,
    sourceBlockId = sourceBlockId,
    blockId = blockId,
    ghost = ghost,
    tintRgb = tintRgb,
    enabled = enabled,
    updatedAt = updatedAt,
    ruleKeys = setOf(ruleKey),
    positions = 1,
)

/** Exact rule first, then the scope's Any block rule, which keeps each block and only tints it. */
internal fun Map<String, BlockAppearance>.typeAppearance(sourceBlockId: String): BlockAppearance? =
    (get(sourceBlockId) ?: get(FME_ANY_SOURCE))?.let { if (it.blockId == FME_ANY_SOURCE) it.copy(blockId = sourceBlockId) else it }

private fun List<FmeTypeReplacement>.toAppearanceMap(): Map<String, BlockAppearance> =
    associate { it.sourceBlockId to BlockAppearance(it.blockId, it.tintRgb, it.properties) }

private val FmeRuleKey.targetable: Boolean get() = ghost && blockId != "minecraft:air"

private fun FmeRuleKey.renderPosition(quarterTurns: Int) = FmeRenderPosition(
    sourceBlockId = sourceBlockId,
    ghost = ghost,
    appearance = BlockAppearance(
        blockId, tintRgb,
        if (quarterTurns == 0) properties else Platform.world.rotateProperties(blockId, properties, quarterTurns),
        reshape = true,
    ),
)

internal fun packFmeRoomCell(worldX: Int, worldZ: Int): Long {
    val cellX = Math.round((worldX - MapState.START_X) / MapState.ROOM_SIZE.toDouble()).toInt()
    val cellZ = Math.round((worldZ - MapState.START_Z) / MapState.ROOM_SIZE.toDouble()).toInt()
    return packInts(cellX, cellZ)
}

private fun roomCellChunks(packed: Long): List<Pair<Int, Int>> {
    val cellX = packed.packedHigh()
    val cellZ = packed.packedLow()
    val centerX = MapState.START_X + cellX * MapState.ROOM_SIZE
    val centerZ = MapState.START_Z + cellZ * MapState.ROOM_SIZE
    // Math.round sends the negative midpoint here, so the range is center - 16..center + 15.
    val minX = centerX - MapState.ROOM_SIZE / 2
    val minZ = centerZ - MapState.ROOM_SIZE / 2
    val maxX = minX + MapState.ROOM_SIZE - 1
    val maxZ = minZ + MapState.ROOM_SIZE - 1
    return buildList {
        for (chunkX in (minX shr 4)..(maxX shr 4)) {
            for (chunkZ in (minZ shr 4)..(maxZ shr 4)) add(chunkX to chunkZ)
        }
    }
}

internal data class FmeRayBoxHit(val face: BlockFace, val distanceSq: Double, val point: Vec3d)

internal fun rayBoxHit(ray: CameraRay, pos: BlockPos3): FmeRayBoxHit? {
    val start = ray.start
    val direction = Vec3d(ray.end.x - start.x, ray.end.y - start.y, ray.end.z - start.z)
    var near = 0.0
    var far = 1.0
    var nearFace: BlockFace? = null

    fun clipAxis(origin: Double, delta: Double, min: Double, max: Double, minFace: BlockFace, maxFace: BlockFace): Boolean {
        if (delta.nearZero()) return origin in min..max
        var first = (min - origin) / delta
        var second = (max - origin) / delta
        var firstFace = minFace
        if (first > second) {
            val swap = first
            first = second
            second = swap
            firstFace = maxFace
        }
        if (first > near) {
            near = first
            nearFace = firstFace
        }
        far = minOf(far, second)
        return near <= far
    }

    if (!clipAxis(start.x, direction.x, pos.x.toDouble(), pos.x + 1.0, BlockFace.WEST, BlockFace.EAST)) return null
    if (!clipAxis(start.y, direction.y, pos.y.toDouble(), pos.y + 1.0, BlockFace.DOWN, BlockFace.UP)) return null
    if (!clipAxis(start.z, direction.z, pos.z.toDouble(), pos.z + 1.0, BlockFace.NORTH, BlockFace.SOUTH)) return null
    val face = nearFace ?: return null
    val point = Vec3d(start.x + direction.x * near, start.y + direction.y * near, start.z + direction.z * near)
    return FmeRayBoxHit(face, start.distanceSqr(point), point)
}

