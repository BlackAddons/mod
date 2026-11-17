package org.blackaddons.platform.impl.world
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.MushroomBlock
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.blackaddons.api.skyblock.dungeon.DungeonUtils
import org.blackaddons.platform.impl.world.BlockHitboxShapes.real
import org.blackaddons.platform.mixin.hitbox.ButtonBlockAccessor
import org.blackaddons.platform.mixin.hitbox.LeverBlockAccessor
import org.blackaddons.platform.mixin.hitbox.MushroomBlockAccessor
import org.blackaddons.platform.mixin.hitbox.SkullBlockAccessor
import java.util.concurrent.ConcurrentHashMap

private typealias AttachedShapes = Map<AttachFace, Map<Direction, VoxelShape>>

/**
 * Shapes for [org.blackaddons.api.world.BlockHitboxes]. Rotations allocate a dozen shapes and `getShape` runs per pick,
 * so each size gets built once. Slider snapping keeps the caches small.
 */
object BlockHitboxShapes {
    private val buttons = ConcurrentHashMap<Double, AttachedShapes>()
    private val pressedButtons = ConcurrentHashMap<Double, AttachedShapes>()
    private val levers = ConcurrentHashMap<Double, AttachedShapes>()

    @JvmStatic
    fun button(state: BlockState, size: Double): VoxelShape {
        val pressed = state.getValue(ButtonBlock.POWERED)
        val cache = if (pressed) pressedButtons else buttons
        val shapes = cache.computeIfAbsent(size) {
            Shapes.rotateAttachFace(Block.boxZ(size * 16.0, if (pressed) 15.0 else 14.0, 16.0))
        }
        return attached(shapes, state)
    }

    @JvmStatic
    fun lever(state: BlockState, size: Double): VoxelShape {
        val shapes = levers.computeIfAbsent(size) {
            Shapes.rotateAttachFace(Block.boxZ(size * 16.0, size * 16.0, 16.0 - size * 16.0, 16.0))
        }
        return attached(shapes, state)
    }

    /** Vanilla's shape from its own table, so no `getShape` hook can enlarge it. Others fall back to `getShape`. */
    @JvmStatic
    fun real(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape = when (val block = state.block) {
        is ButtonBlock -> (block as ButtonBlockAccessor).`blackaddons$getShapes`().apply(state)
        is LeverBlock -> (block as LeverBlockAccessor).`blackaddons$getShapes`().apply(state)
        is MushroomBlock -> MushroomBlockAccessor.`blackaddons$getShape`()
        is SkullBlock -> if (block.type == SkullBlock.Types.PIGLIN) {
            SkullBlockAccessor.`blackaddons$getPiglinShape`()
        } else {
            SkullBlockAccessor.`blackaddons$getShape`()
        }
        else -> state.getShape(level, pos)
    }

    /** Outline ray test against [real] shapes, ignores fluids. */
    @JvmStatic
    fun clip(level: BlockGetter, from: Vec3, to: Vec3): BlockHitResult = BlockGetter.traverseBlocks(
        from, to, level,
        { getter, pos ->
            val state = getter.getBlockState(pos)
            getter.clipWithInteractionOverride(from, to, pos, real(state, getter, pos), state)
        },
        { _ ->
            val back = from.subtract(to)
            BlockHitResult.miss(to, Direction.getApproximateNearest(back.x, back.y, back.z), BlockPos.containing(to))
        },
    )

    @JvmStatic
    fun isSecretSkull(level: BlockGetter, pos: BlockPos): Boolean {
        val skull = level.getBlockEntity(pos) as? SkullBlockEntity ?: return false
        return DungeonUtils.isSecretSkull(skull.ownerProfile?.partialProfile()?.id()?.toString())
    }

    private fun attached(shapes: AttachedShapes, state: BlockState): VoxelShape =
        shapes.getValue(state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE))
            .getValue(state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING))
}
