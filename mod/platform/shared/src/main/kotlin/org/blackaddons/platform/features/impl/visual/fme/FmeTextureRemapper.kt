package org.blackaddons.platform.features.impl.visual.fme

import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.builders.UVPair
import net.minecraft.client.renderer.block.BlockStateModelSet
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.Direction
import net.minecraft.data.AtlasIds
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.state.BlockState
import org.blackaddons.core.features.impl.visual.fme.FmeTextureCoordinates
import org.blackaddons.platform.compat.blockMaterial
import org.blackaddons.platform.compat.withSprite
import org.joml.Vector3fc
import java.util.concurrent.ConcurrentHashMap

/** Retextures existing quads so the source block's geometry stays in the chunk mesh. */
object FmeTextureRemapper {
    /** [inset]: fraction of [sprite] per side that faces don't map to ([FmeEntitySpriteSource.CHEST_INSET]). */
    data class Face(val sprite: TextureAtlasSprite, val material: BakedQuad.MaterialInfo?, val inset: Float = 0f) {
        val u0: Float get() = sprite.u0 + (sprite.u1 - sprite.u0) * inset
        val u1: Float get() = sprite.u1 - (sprite.u1 - sprite.u0) * inset
        val v0: Float get() = sprite.v0 + (sprite.v1 - sprite.v0) * inset
        val v1: Float get() = sprite.v1 - (sprite.v1 - sprite.v0) * inset
    }

    private data class QuadKey(val quad: BakedQuad, val target: BlockState)

    @Volatile private var modelSet: BlockStateModelSet? = null
    private val faces = ConcurrentHashMap<BlockState, Map<Direction, Face>>()
    private val quads = ConcurrentHashMap<QuadKey, BakedQuad>()

    @JvmStatic
    fun faceFor(target: BlockState, direction: Direction): Face {
        val set = currentModelSet()
        return faces.computeIfAbsent(target) { state ->
            if (FmeEntityTextureProviders.supports(state)) {
                // Chunk-compile worker: TextureManager.getTexture would load on the render system.
                val atlas = Minecraft.getInstance().atlasManager.getAtlasOrThrow(AtlasIds.BLOCKS)
                val shulker = state.block is ShulkerBoxBlock
                val layer = if (shulker) ChunkSectionLayer.CUTOUT else ChunkSectionLayer.SOLID
                val inset = if (shulker) 0f else FmeEntitySpriteSource.CHEST_INSET
                return@computeIfAbsent Direction.entries.associateWith { face ->
                    val sprite = atlas.getSprite(FmeEntityTextureProviders.faceSprite(state, face)!!)
                    Face(sprite, blockMaterial(sprite, layer), inset)
                }
            }
            val model = set.get(state)
            val parts = ArrayList<BlockStateModelPart>()
            model.collectParts(RandomSource.create(0L), parts)
            val baked = parts.flatMap { part ->
                Direction.entries.flatMap(part::getQuads) + part.getQuads(null)
            }
            val particle = model.particleMaterial().sprite()
            Direction.entries.associateWith { face ->
                val material = baked.firstOrNull { it.direction() == face }?.materialInfo()
                    ?: baked.firstOrNull()?.materialInfo()
                Face(material?.sprite() ?: particle, material)
            }
        }.getValue(direction)
    }

    @JvmStatic
    fun retexture(quad: BakedQuad, target: BlockState): BakedQuad {
        currentModelSet()
        return quads.computeIfAbsent(QuadKey(quad, target)) {
            val source = quad.materialInfo()
            val face = faceFor(target, quad.direction())
            val replacement = face.material
            val material = source.withSprite(face.sprite, replacement)
            BakedQuad(
                quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                remapUv(quad.packedUV0(), quad.position0(), source.sprite(), face, target, quad.direction()),
                remapUv(quad.packedUV1(), quad.position1(), source.sprite(), face, target, quad.direction()),
                remapUv(quad.packedUV2(), quad.position2(), source.sprite(), face, target, quad.direction()),
                remapUv(quad.packedUV3(), quad.position3(), source.sprite(), face, target, quad.direction()),
                quad.direction(), material,
            )
        }
    }

    @JvmStatic
    fun mapUv(
        u: Float,
        v: Float,
        source: TextureAtlasSprite,
        targetFace: Face,
        target: BlockState,
        direction: Direction,
        x: Float,
        y: Float,
        z: Float,
    ): Long {
        val projected = FmeEntityTextureProviders.projectedUv(target, direction, x, y, z)
        if (projected == null) return UVPair.pack(mapU(u, source, targetFace), mapV(v, source, targetFace))
        val projectedU = UVPair.unpackU(projected)
        val projectedV = UVPair.unpackV(projected)
        return UVPair.pack(
            targetFace.u0 + projectedU * (targetFace.u1 - targetFace.u0),
            targetFace.v0 + projectedV * (targetFace.v1 - targetFace.v0),
        )
    }

    @JvmStatic
    fun mapU(u: Float, source: TextureAtlasSprite, target: Face): Float =
        FmeTextureCoordinates.remap(u, source.u0, source.u1, target.u0, target.u1)

    @JvmStatic
    fun mapV(v: Float, source: TextureAtlasSprite, target: Face): Float =
        FmeTextureCoordinates.remap(v, source.v0, source.v1, target.v0, target.v1)

    private fun remapUv(
        uv: Long,
        position: Vector3fc,
        source: TextureAtlasSprite,
        face: Face,
        target: BlockState,
        direction: Direction,
    ): Long = mapUv(
        UVPair.unpackU(uv), UVPair.unpackV(uv), source, face, target, direction,
        position.x(), position.y(), position.z(),
    )

    private fun currentModelSet(): BlockStateModelSet {
        val current = Minecraft.getInstance().modelManager.blockStateModelSet
        if (current !== modelSet) synchronized(this) {
            if (current !== modelSet) {
                faces.clear()
                quads.clear()
                modelSet = current
            }
        }
        return current
    }
}
