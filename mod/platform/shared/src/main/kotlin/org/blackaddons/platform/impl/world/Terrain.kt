package org.blackaddons.platform.impl.world

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.level.block.WebBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.pathfinder.PathType
import org.blackaddons.api.event.world.BlockChangeEvent
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.BlockTerrain
import org.blackaddons.api.world.Box3d
import org.blackaddons.api.world.TerrainFluid
import org.blackaddons.api.world.TerrainSnapshot
import org.blackaddons.api.world.Vec3d
import org.blackaddons.platform.mixin.world.WalkNodeEvaluatorInvoker
import java.util.IdentityHashMap

/** Builds [BlockTerrain] from a live [BlockState], and [TerrainSnapshot]s of a level. Client thread only. */
internal object Terrain {

    private val DAMAGING = setOf(PathType.LAVA, PathType.FIRE, PathType.DAMAGING, PathType.DAMAGE_CAUTIOUS)
    private val SLOWING = setOf(PathType.POWDER_SNOW, PathType.STICKY_HONEY)

    /** [state] as if it stood at [pos], no matter what [level] holds there now. */
    fun of(state: BlockState, level: BlockGetter, pos: BlockPos): BlockTerrain {
        val at = if (level.getBlockState(pos) === state) level else OneBlockOverride(level, pos, state)
        val block = state.block
        val fluid = state.fluidState
        val pathType = WalkNodeEvaluatorInvoker.`blackaddons$pathTypeOf`(at, pos)
        return BlockTerrain(
            blockId = BuiltInRegistries.BLOCK.getKey(block).toString(),
            properties = state.properties.associate { it.name to state.valueName(it) },
            collisionBoxes = state.getCollisionShape(at, pos).toAabbs().map {
                Box3d(Vec3d(it.minX, it.minY, it.minZ), Vec3d(it.maxX, it.maxY, it.maxZ))
            },
            fluid = when {
                fluid.`is`(FluidTags.WATER) -> TerrainFluid.WATER
                fluid.`is`(FluidTags.LAVA) -> TerrainFluid.LAVA
                else -> null
            },
            climbable = state.`is`(BlockTags.CLIMBABLE),
            damaging = pathType in DAMAGING,
            // Vanilla's path types only cover some of them, and webs and soul sand slow u down without one.
            slowing = pathType in SLOWING || block.speedFactor < 1f || block is WebBlock || block is PowderSnowBlock,
        )
    }

    fun snapshot(level: ClientLevel, minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): TerrainSnapshot {
        val sizeX = maxX - minX + 1
        val sizeY = maxY - minY + 1
        val sizeZ = maxZ - minZ + 1
        val cellCount = sizeX.toLong() * sizeY * sizeZ
        require(cellCount <= TerrainSnapshot.MAX_CELLS) { "$cellCount cells is over ${TerrainSnapshot.MAX_CELLS}" }

        val palette = arrayListOf(BlockTerrain.UNLOADED)
        val indexOf = IdentityHashMap<BlockState, Int>()
        val cells = ShortArray(cellCount.toInt())
        val pos = BlockPos.MutableBlockPos()
        for (chunkX in (minX shr 4)..(maxX shr 4)) for (chunkZ in (minZ shr 4)..(maxZ shr 4)) {
            // Cells of unloaded chunks stay at UNLOADED_INDEX, which a fresh array already holds.
            val chunk = level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: continue
            for (x in maxOf(minX, chunkX shl 4)..minOf(maxX, (chunkX shl 4) + 15)) {
                for (z in maxOf(minZ, chunkZ shl 4)..minOf(maxZ, (chunkZ shl 4) + 15)) {
                    for (y in minY..maxY) {
                        val state = chunk.getBlockState(pos.set(x, y, z))
                        val index = indexOf.getOrPut(state) {
                            palette += of(state, level, pos.immutable())
                            palette.size - 1
                        }
                        cells[((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX)] = index.toShort()
                    }
                }
            }
        }
        return TerrainSnapshot(minX, minY, minZ, sizeX, sizeY, sizeZ, palette, cells)
    }

    fun chunkSnapshot(level: ClientLevel, chunkX: Int, chunkZ: Int): TerrainSnapshot? {
        level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return null
        val x = chunkX shl 4
        val z = chunkZ shl 4
        return snapshot(level, x, level.minY, z, x + 15, level.maxY, z + 15)
    }

    private fun <T : Comparable<T>> BlockState.valueName(property: Property<T>): String = property.getName(getValue(property))
}

/** Lazy, because most listeners only look at [pos]. Built on the client thread inside the post. */
internal class MinecraftBlockChange(
    level: ClientLevel,
    pos: BlockPos,
    beforeState: BlockState,
    afterState: BlockState,
) : BlockChangeEvent(BlockPos3(pos.x, pos.y, pos.z)) {
    override val before: BlockTerrain by lazy(LazyThreadSafetyMode.NONE) { Terrain.of(beforeState, level, pos) }
    override val after: BlockTerrain by lazy(LazyThreadSafetyMode.NONE) { Terrain.of(afterState, level, pos) }
}

/** [level] with one block swapped, to describe a state that isn't there (or isn't there anymore). */
private class OneBlockOverride(
    private val level: BlockGetter,
    private val pos: BlockPos,
    private val state: BlockState,
) : BlockGetter {
    override fun getBlockState(at: BlockPos): BlockState = if (at == pos) state else level.getBlockState(at)

    override fun getFluidState(at: BlockPos): FluidState = getBlockState(at).fluidState

    override fun getBlockEntity(at: BlockPos): BlockEntity? = if (at == pos) null else level.getBlockEntity(at)

    override fun getHeight(): Int = level.height

    override fun getMinY(): Int = level.minY
}
