package org.blackaddons.api.world


/** Fluid in a block, waterlogged blocks included. */
enum class TerrainFluid { WATER, LAVA }

/**
 * What one block state means for moving through it. It is computed from the game's own shapes and rules instead of a
 * table of ids, so slabs, stairs, carpets and waterlogged blocks come out right.
 */
data class BlockTerrain(
    /** For example `"minecraft:oak_slab"`. Empty for [UNLOADED]. */
    val blockId: String,
    /** Every state property as the game spells it, for example `type` to `bottom`. */
    val properties: Map<String, String>,
    /**
     * In block-local coordinates from 0 to 1 (fences and walls reach 1.5). They come from a collision check without an
     * entity, so powder snow has none, and offset plants use the position where they were first seen.
     */
    val collisionBoxes: List<Box3d>,
    val fluid: TerrainFluid?,
    /** Ladders, vines, scaffolding and the like. */
    val climbable: Boolean,
    /** Hurts on contact or on landing: fire, lava, magma, cactus, berry bushes, wither roses, dripstone. */
    val damaging: Boolean,
    /** Slows or traps: cobwebs, soul sand, honey, powder snow. */
    val slowing: Boolean,
) {
    val collides: Boolean get() = collisionBoxes.isNotEmpty()

    /** The height an entity stands at above the block's floor. 0 without collision. */
    val collisionTop: Double = collisionBoxes.maxOfOrNull { it.max.y } ?: 0.0

    val isFullCube: Boolean = collisionBoxes.singleOrNull() == FULL_CUBE

    /** False only for [UNLOADED]. */
    val isLoaded: Boolean get() = this !== UNLOADED

    companion object {
        private val FULL_CUBE = Box3d(Vec3d(0.0, 0.0, 0.0), Vec3d(1.0, 1.0, 1.0))

        /** A cell in a chunk that the client does not have: unknown, not air. */
        val UNLOADED = BlockTerrain("", emptyMap(), emptyList(), null, climbable = false, damaging = false, slowing = false)
    }
}

/**
 * The blocks of a box, copied in one pass on the client thread and readable from any thread afterward. Each cell is an
 * index into [palette], which has one entry per distinct block state, the way a chunk section stores blocks. Cells in
 * chunks that the client has not loaded read [BlockTerrain.UNLOADED], which is always palette entry [UNLOADED_INDEX].
 *
 * Public, so a cache can rebuild a snapshot from its own storage with [cells].
 */
class TerrainSnapshot(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val palette: List<BlockTerrain>,
    private val cells: ShortArray,
) {
    init {
        require(sizeX > 0 && sizeY > 0 && sizeZ > 0) { "Empty box $sizeX x $sizeY x $sizeZ" }
        require(cells.size == sizeX * sizeY * sizeZ) { "${cells.size} cells for $sizeX x $sizeY x $sizeZ" }
        require(palette.firstOrNull() === BlockTerrain.UNLOADED) { "Palette entry $UNLOADED_INDEX must be UNLOADED" }
    }

    val maxX: Int get() = minX + sizeX - 1
    val maxY: Int get() = minY + sizeY - 1
    val maxZ: Int get() = minZ + sizeZ - 1

    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in minX..maxX && y in minY..maxY && z in minZ..maxZ

    /** The fast path for a planner's inner loop. Throws outside the box. */
    fun paletteIndex(x: Int, y: Int, z: Int): Int {
        if (!contains(x, y, z)) throw IndexOutOfBoundsException("($x, $y, $z) outside $minX..$maxX, $minY..$maxY, $minZ..$maxZ")
        return cells[((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX)].toInt() and 0xFFFF
    }

    /** Throws outside the box. */
    operator fun get(x: Int, y: Int, z: Int): BlockTerrain = palette[paletteIndex(x, y, z)]

    /** Palette indices, ordered y, then z, then x, from outermost to innermost. Returns a copy. */
    fun cells(): ShortArray = cells.copyOf()

    companion object {
        /** The largest box that one call copies, about one frame of client thread time. */
        const val MAX_CELLS = 1 shl 20

        const val UNLOADED_INDEX = 0
    }
}
