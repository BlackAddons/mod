package org.blackaddons.platform.features.impl.visual.fme

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.StandingAndWallBlockItem
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import org.blackaddons.api.world.BlockAppearance
import org.blackaddons.api.world.BlockAppearances
import org.blackaddons.core.features.impl.visual.fme.Fme
import org.blackaddons.platform.mixin.fme.StandingAndWallBlockItemAccessor
import org.blackaddons.platform.util.withProperties
import java.util.concurrent.ConcurrentHashMap

/** Resolves [BlockAppearances]' version-neutral ids into render-only states. */
object FmeBlockRemapper {
    private const val THREAD_CACHE_LIMIT = 8192
    private val blockIds = ConcurrentHashMap<Block, String>()
    private val standingIds = ConcurrentHashMap<Block, String>()
    private val placedStates = ConcurrentHashMap<Pair<String, Map<String, String>>, BlockState>()
    private val baseStates = ConcurrentHashMap<String, BlockState>()
    private val fullCubes = ConcurrentHashMap<BlockState, Boolean>()
    private val tints = ThreadLocal.withInitial { HashMap<Long, Int>() }
    private val blockEntityStates = ThreadLocal.withInitial { HashMap<Long, BlockState>() }
    private val textureStates = ThreadLocal.withInitial { HashMap<Long, BlockState>() }
    private val shaping = ThreadLocal.withInitial { false }
    private val random = ThreadLocal.withInitial(RandomSource::create)

    /** Cached [blockEntityTint] answers, valid for the current render index and block state. Render thread only. */
    private class BlockEntityTint(val state: BlockState, val tint: Int)

    private val blockEntityTints = HashMap<Long, BlockEntityTint>()
    private var blockEntityTintsFor: Any? = null

    @JvmStatic
    fun remap(original: BlockState, x: Int, y: Int, z: Int, region: BlockGetter, level: Level): BlockState {
        if (shaping.get()) {
            val appearance = appearanceOf(original, x, y, z) ?: return original
            val mapped = mappedState(original, appearance) ?: return original
            return if (textureOnly(original, mapped, appearance)) original else mapped
        }
        val key = pack(x, y, z)
        val appearance = appearanceOf(original, x, y, z)
        if (appearance == null) {
            tints.get().remove(key)
            blockEntityStates.get().remove(key)
            textureStates.get().remove(key)
            return original
        }
        rememberTint(key, appearance.tintRgb)

        val mapped = mappedState(original, appearance)
        if (mapped == null) {
            blockEntityStates.get().remove(key)
            textureStates.get().remove(key)
            return original
        }
        if (textureOnly(original, mapped, appearance)) {
            val cache = textureStates.get()
            if (cache.size >= THREAD_CACHE_LIMIT) cache.clear()
            cache[key] = mapped
            blockEntityStates.get().remove(key)
            return original
        }
        textureStates.get().remove(key)
        val shaped = shape(mapped, appearance.properties, x, y, z, region, level)
        rememberBlockEntityState(key, original, shaped)
        return shaped
    }

    /* Null for air, FME never draws it. */
    private fun mappedState(original: BlockState, appearance: BlockAppearance): BlockState? {
        val base = baseStates.computeIfAbsent(appearance.blockId, ::resolveBaseState)
        var mapped = if (appearance.properties.isEmpty() || base.isAir) base else {
            placedStates.computeIfAbsent(appearance.blockId to appearance.properties) { base.withProperties(appearance.properties) }
        }
        if (mapped.isAir) return null
        original.properties.forEach { property ->
            if (mapped.hasProperty(property) && property.name !in appearance.properties) {
                mapped = copyProperty(mapped, original, property)
            }
        }
        return mapped
    }

    /* An edit of the same block draws its own state. Retexturing the real one would drop connections and pins. */
    private fun textureOnly(original: BlockState, mapped: BlockState, appearance: BlockAppearance): Boolean =
        Fme.preserveBlockShape() && !original.isAir && !original.hasBlockEntity() &&
            !(appearance.reshape && mapped.block === original.block) &&
            !seeThroughOverSolid(original, mapped) && original.renderShape == RenderShape.MODEL &&
            original.fluidState.isEmpty && mapped.fluidState.isEmpty &&
            (mapped.renderShape == RenderShape.MODEL || FmeEntityTextureProviders.supports(mapped))

    /*
     * Runs vanilla's neighbor updates against what's drawn, so visual walls, fences and panes connect like placed ones.
     * A step that would change the block (an unsupported plant, a lone door half) gets skipped, and stored state wins.
     * shortcut: reshapes on every read of the position, six or more neighbor reads each; cache per section build if
     * spark shows it.
     */
    private fun shape(
        state: BlockState,
        stored: Map<String, String>,
        x: Int,
        y: Int,
        z: Int,
        region: BlockGetter,
        level: Level,
    ): BlockState {
        if (state.properties.isEmpty()) return state
        val pos = BlockPos(x, y, z)
        val reader = FmeShapeLevel(region, level)
        shaping.set(true)
        try {
            var shaped = state
            for (direction in Direction.entries) {
                val neighborPos = pos.relative(direction)
                val neighbor = region.getBlockState(neighborPos)
                val next = shaped.updateShape(reader, level, pos, direction, neighborPos, neighbor, random.get())
                if (next.block === shaped.block) shaped = next
            }
            return if (stored.isEmpty()) shaped else shaped.withProperties(stored)
        } finally {
            shaping.set(false)
        }
    }

    private fun seeThroughOverSolid(original: BlockState, mapped: BlockState): Boolean =
        original.isSolidRender && !mapped.isSolidRender &&
                fullCubes.computeIfAbsent(mapped) { Block.isShapeFullBlock(it.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) }

    @JvmStatic
    fun remapBlockEntity(existing: BlockEntity?, level: Level, pos: BlockPos, mapped: BlockState): BlockEntity? {
        val key = pack(pos.x, pos.y, pos.z)
        val target = blockEntityStates.get().remove(key)?.takeIf { it === mapped } ?: return existing
        val entityBlock = target.block as? EntityBlock ?: return existing
        return entityBlock.newBlockEntity(pos, target)?.also { it.setLevel(level) } ?: existing
    }

    /** Tint of the block entity being submitted. Block entities skip the chunk mesher, so their submits read this. */
    @JvmStatic
    var submittingBlockEntityTint = 0xFFFFFF

    @JvmStatic
    fun blockEntityTint(level: Level, pos: BlockPos): Int {
        if (!Fme.renderTinted()) return 0xFFFFFF
        val revision = Fme.renderRevision()
        if (revision !== blockEntityTintsFor) {
            blockEntityTints.clear()
            blockEntityTintsFor = revision
        }
        val state = level.getBlockState(pos)
        val key = pack(pos.x, pos.y, pos.z)
        blockEntityTints[key]?.let { if (it.state === state) return it.tint }
        val tint = appearanceOf(state, pos.x, pos.y, pos.z)?.tintRgb ?: 0xFFFFFF
        if (blockEntityTints.size >= THREAD_CACHE_LIMIT) blockEntityTints.clear()
        blockEntityTints[key] = BlockEntityTint(state, tint)
        return tint
    }

    @JvmStatic
    fun tintAt(x: Int, y: Int, z: Int): Int = tints.get()[pack(x, y, z)] ?: 0xFFFFFF

    @JvmStatic
    fun textureStateAt(x: Int, y: Int, z: Int): BlockState? = textureStates.get()[pack(x, y, z)]

    /**
     * Is [neighbor]'s [face] see-through, so [state] behind it keeps its face? Looking alike (glass on glass) still
     * culls. Only valid right after the mesher read it.
     */
    @JvmStatic
    fun seeThroughFace(state: BlockState, neighbor: BlockPos, face: Direction): Boolean {
        val texture = textureStateAt(neighbor.x, neighbor.y, neighbor.z) ?: return false
        val layer = FmeTextureRemapper.faceFor(texture, face).material?.layer() ?: return false
        if (layer == ChunkSectionLayer.SOLID) return false
        val visual = textureStateAt(neighbor.x + face.stepX, neighbor.y + face.stepY, neighbor.z + face.stepZ) ?: state
        return !visual.skipRendering(texture, face.opposite)
    }

    /**
     * Biome color for the target's face. Retextured quads drop their tint index (it's the source block's), so grass and
     * leaves went gray.
     */
    @JvmStatic
    fun textureTint(target: BlockState, face: Direction, level: BlockAndTintGetter, pos: BlockPos): Int {
        val index = FmeTextureRemapper.faceFor(target, face).material?.tintIndex() ?: return 0xFFFFFF
        val source = Minecraft.getInstance().blockColors.getTintSources(target).getOrNull(index) ?: return 0xFFFFFF
        return source.colorInWorld(target, level, pos)
    }

    @JvmStatic
    fun multiplyTint(color: Int, tint: Int): Int {
        val red = multiplyChannel(color shr 16 and 0xFF, tint shr 16 and 0xFF)
        val green = multiplyChannel(color shr 8 and 0xFF, tint shr 8 and 0xFF)
        val blue = multiplyChannel(color and 0xFF, tint and 0xFF)
        return color and -0x1000000 or (red shl 16) or (green shl 8) or blue
    }

    private fun appearanceOf(original: BlockState, x: Int, y: Int, z: Int): BlockAppearance? {
        if (!BlockAppearances.covers(x, y, z)) return null
        val originalId = idOf(original.block)
        BlockAppearances.appearanceAt(x, y, z, originalId, original.isAir)?.let { return it }
        val standingId = standingIds.computeIfAbsent(original.block) { block ->
            (block.asItem() as? BlockItem)?.block?.takeIf { it !== block && !it.defaultBlockState().isAir }?.let(::idOf) ?: ""
        }
        if (original.isAir || standingId.isEmpty()) return null
        val standing = BlockAppearances.appearanceAt(x, y, z, standingId, false) ?: return null
        return standing.copy(blockId = if (standing.blockId == standingId) originalId else wallVariantId(standing.blockId))
    }

    /** Wall variants share their standing block's item, so its rule covers them without making them upright. */
    private fun wallVariantId(blockId: String): String {
        val block = baseStates.computeIfAbsent(blockId, ::resolveBaseState).block
        val item = block.asItem() as? StandingAndWallBlockItem ?: return blockId
        return idOf((item as StandingAndWallBlockItemAccessor).`blackaddons$getWallBlock`())
    }

    private fun idOf(block: Block): String = blockIds.computeIfAbsent(block) {
        BuiltInRegistries.BLOCK.getKey(it).toString()
    }

    private fun resolveBaseState(blockId: String): BlockState {
        val id = Identifier.tryParse(blockId) ?: return Blocks.AIR.defaultBlockState()
        return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState()
    }

    private fun rememberTint(key: Long, tint: Int) {
        val cache = tints.get()
        if (tint == 0xFFFFFF) {
            cache.remove(key)
            return
        }
        if (cache.size >= THREAD_CACHE_LIMIT) cache.clear()
        cache[key] = tint
    }

    private fun rememberBlockEntityState(key: Long, original: BlockState, mapped: BlockState) {
        val cache = blockEntityStates.get()
        if (mapped === original || !mapped.hasBlockEntity()) {
            cache.remove(key)
            return
        }
        if (cache.size >= THREAD_CACHE_LIMIT) cache.clear()
        cache[key] = mapped
    }

    private fun multiplyChannel(channel: Int, tint: Int): Int = (channel * tint + 127) / 255

    private fun pack(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFFL) shl 38 or ((z.toLong() and 0x3FFFFFFL) shl 12) or (y.toLong() and 0xFFFL)

    private fun <T : Comparable<T>> copyProperty(into: BlockState, from: BlockState, property: Property<T>): BlockState =
        into.setValue(property, from.getValue(property))
}
