package org.blackaddons.platform.impl.player
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.blackaddons.api.camera.CameraControllers
import org.blackaddons.api.player.BlockFace
import org.blackaddons.api.player.CameraRay
import org.blackaddons.api.player.RaycastHit
import org.blackaddons.api.player.RaycastHost
import org.blackaddons.api.world.BlockHitboxes
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d
import org.blackaddons.platform.compat.mainCamera
import org.blackaddons.platform.impl.world.BlockHitboxShapes
import org.blackaddons.platform.impl.world.EntitySnapshots
import org.blackaddons.platform.util.readOnClient

/** [RaycastHost]: vanilla block pick plus a manual entity sweep (the game's entity pick is private). */
object MinecraftRaycast : RaycastHost {

    private val mc get() = Minecraft.getInstance()

    // Each query reads tick-mutated state, so it hops.

    override fun lookingAt(maxDistance: Double, includeEntities: Boolean): RaycastHit? =
        readOnClient { lookingAt(maxDistance, includeEntities, controlledCamera = false) }

    override fun lookingAtCamera(maxDistance: Double, includeEntities: Boolean): RaycastHit? =
        readOnClient { lookingAt(maxDistance, includeEntities, controlledCamera = true) }

    override fun cameraRay(maxDistance: Double): CameraRay? = readOnClient { ray(maxDistance) }

    override fun blockToward(target: Vec3d, maxDistance: Double): RaycastHit.Block? =
        readOnClient { blockHit(target, maxDistance) }

    private fun ray(maxDistance: Double): CameraRay? {
        val player = mc.player ?: return null
        mc.level ?: return null
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val controller = CameraControllers.active()
        val eye = if (controller == null) player.getEyePosition(partialTick) else mc.mainCamera.position()
        val view = if (controller == null) {
            player.getViewVector(partialTick)
        } else {
            Vec3.directionFromRotation(mc.mainCamera.xRot(), mc.mainCamera.yRot())
        }
        return CameraRay(eye.toVec3d(), eye.add(view.scale(maxDistance)).toVec3d())
    }

    private fun lookingAt(maxDistance: Double, includeEntities: Boolean, controlledCamera: Boolean): RaycastHit? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null

        val controller = if (controlledCamera) CameraControllers.active() else null
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val eye = if (controller == null) {
            player.getEyePosition(partialTick)
        } else {
            mc.mainCamera.position()
        }
        val view = if (controller == null) {
            player.getViewVector(partialTick)
        } else {
            Vec3.directionFromRotation(mc.mainCamera.xRot(), mc.mainCamera.yRot())
        }
        val end = eye.add(view.scale(maxDistance))
        val blockHit = when {
            BlockHitboxes.realShapes -> BlockHitboxShapes.clip(level, eye, end)
            controller == null -> player.pick(maxDistance, partialTick, false)
            else -> level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        }
        var closestDistSq =
            if (blockHit.type != HitResult.Type.MISS) blockHit.location.distanceToSqr(eye)
            else maxDistance * maxDistance

        if (includeEntities) {
            val searchBox = if (controller == null) {
                player.boundingBox.expandTowards(view.x * maxDistance, view.y * maxDistance, view.z * maxDistance)
            } else {
                AABB(eye, end)
            }.inflate(1.0, 1.0, 1.0)

            var closest: Entity? = null
            var closestPoint: Vec3? = null
            for (entity in level.getEntities(player, searchBox) { !it.isSpectator && it.isPickable }) {
                val aabb = entity.boundingBox.inflate(entity.pickRadius.toDouble())
                val clip = aabb.clip(eye, end)
                if (aabb.contains(eye)) {
                    closest = entity
                    closestPoint = clip.orElse(eye)
                    closestDistSq = 0.0
                } else if (clip.isPresent) {
                    val point = clip.get()
                    val distSq = eye.distanceToSqr(point)
                    if (distSq < closestDistSq) {
                        closest = entity
                        closestPoint = point
                        closestDistSq = distSq
                    }
                }
            }
            if (closest != null && closestPoint != null) {
                return RaycastHit.Entity(EntitySnapshots.of(closest), closestPoint.toVec3d(), closestDistSq)
            }
        }

        return (blockHit as? BlockHitResult)?.toHit(eye)
    }

    private fun blockHit(target: Vec3d, maxDistance: Double): RaycastHit.Block? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val eye = player.eyePosition
        val direction = Vec3(target.x - eye.x, target.y - eye.y, target.z - eye.z).normalize()
        val end = eye.add(direction.scale(maxDistance))
        val hit = if (BlockHitboxes.realShapes) {
            BlockHitboxShapes.clip(level, eye, end)
        } else {
            level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        }
        return hit.toHit(eye)
    }

    private fun BlockHitResult.toHit(eye: Vec3): RaycastHit.Block? {
        if (type != HitResult.Type.BLOCK) return null
        val level = mc.level ?: return null
        val id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(blockPos).block).toString()
        return RaycastHit.Block(
            BlockPos3(blockPos.x, blockPos.y, blockPos.z),
            id,
            location.toVec3d(),
            location.distanceToSqr(eye),
            BlockFace.valueOf(direction.name),
        )
    }

    private fun Vec3.toVec3d() = Vec3d(x, y, z)
}
