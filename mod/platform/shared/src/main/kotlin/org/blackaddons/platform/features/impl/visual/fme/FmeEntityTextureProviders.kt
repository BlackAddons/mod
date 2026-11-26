package org.blackaddons.platform.features.impl.visual.fme

import net.minecraft.client.model.geom.builders.UVPair
import net.minecraft.client.renderer.blockentity.ChestRenderer
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import org.joml.Matrix4fc
import org.joml.Vector3f

/** Baked faces for block entities whose closed skins can go onto chunk geometry. */
object FmeEntityTextureProviders {
    private data class ShulkerTransform(
        val inverse: Matrix4fc,
        val modelFaces: Map<Direction, Direction>,
    )

    private val shulkerTransforms by lazy {
        Direction.values().associateWith { facing ->
            val inverse = requireNotNull(ShulkerBoxRenderer.modelTransform(facing).inverse()).matrix
            val faces = Direction.values().associateWith { worldFace -> modelFace(inverse, worldFace) }
            ShulkerTransform(inverse, faces)
        }
    }
    private val projectedPosition = ThreadLocal.withInitial { Vector3f() }

    @JvmStatic
    fun supports(state: BlockState): Boolean {
        val block = state.block
        return block is ShulkerBoxBlock || block === Blocks.ENDER_CHEST ||
            block is ChestBlock && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
    }

    fun faceSprite(state: BlockState, face: Direction): Identifier? {
        val (kind, skin) = skinFor(state) ?: return null
        val modelFace = when (kind) {
            "shulker" -> shulkerTransforms.getValue(state.getValue(ShulkerBoxBlock.FACING)).modelFaces.getValue(face)
            else -> chestModelFace(state, face)
        }
        return FmeEntitySpriteSource.spriteId(kind, Identifier.withDefaultNamespace(skin), modelFace)
    }

    fun projectedUv(state: BlockState, worldFace: Direction, x: Float, y: Float, z: Float): Long? {
        if (state.block !is ShulkerBoxBlock) return null
        val transform = shulkerTransforms.getValue(state.getValue(ShulkerBoxBlock.FACING))
        val position = projectedPosition.get().set(x, y, z)
        transform.inverse.transformPosition(position)
        val modelPositionX = (position.x + 0.5f) * 16f
        val modelPositionY = position.y * 16f
        val modelPositionZ = (position.z + 0.5f) * 16f
        val modelFace = transform.modelFaces.getValue(worldFace)
        val (u, v) = when (modelFace) {
            Direction.UP -> modelPositionX to modelPositionZ
            Direction.DOWN -> modelPositionX to (16f - modelPositionZ)
            Direction.NORTH -> modelPositionX to (modelPositionY - 8f)
            Direction.SOUTH -> (16f - modelPositionX) to (modelPositionY - 8f)
            Direction.WEST -> (16f - modelPositionZ) to (modelPositionY - 8f)
            Direction.EAST -> modelPositionZ to (modelPositionY - 8f)
        }
        return UVPair.pack((u / 16f).coerceIn(0f, 1f), (v / 16f).coerceIn(0f, 1f))
    }

    private fun chestModelFace(state: BlockState, worldFace: Direction): Direction {
        if (worldFace.axis == Direction.Axis.Y) return worldFace
        val facing = if (state.block === Blocks.ENDER_CHEST) state.getValue(EnderChestBlock.FACING)
        else state.getValue(ChestBlock.FACING)
        var modelFace = worldFace
        var current = facing
        while (current != Direction.SOUTH) {
            current = current.clockWise
            modelFace = modelFace.clockWise
        }
        return modelFace
    }

    private fun modelFace(inverse: Matrix4fc, worldFace: Direction): Direction {
        val normal = Vector3f(worldFace.stepX.toFloat(), worldFace.stepY.toFloat(), worldFace.stepZ.toFloat())
        inverse.transformDirection(normal)
        return Direction.values().maxBy { direction ->
            normal.x * direction.stepX + normal.y * direction.stepY + normal.z * direction.stepZ
        }
    }

    private fun skinFor(state: BlockState): Pair<String, String>? {
        val block = state.block
        if (block is ShulkerBoxBlock) {
            val color = block.color?.name?.lowercase()
            return "shulker" to if (color == null) "shulker" else "shulker_$color"
        }
        if (block === Blocks.ENDER_CHEST) return "chest" to "ender"
        if (block !is ChestBlock || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) return null
        if (block !is CopperChestBlock && ChestRenderer.xmasTextures()) return "chest" to "christmas"
        val skin = when (block) {
            Blocks.TRAPPED_CHEST -> "trapped"
            is CopperChestBlock -> when (block.state) {
                WeatheringCopper.WeatherState.UNAFFECTED -> "copper"
                WeatheringCopper.WeatherState.EXPOSED -> "copper_exposed"
                WeatheringCopper.WeatherState.WEATHERED -> "copper_weathered"
                WeatheringCopper.WeatherState.OXIDIZED -> "copper_oxidized"
            }
            else -> "normal"
        }
        return "chest" to skin
    }
}
