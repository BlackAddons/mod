package org.blackaddons.api.player

import org.blackaddons.api.event.world.EntitySnapshot
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d

sealed interface RaycastHit {
    val point: Vec3d

    /** Squared distance from the eyes to [point]. */
    val distanceSq: Double

    /** [blockId] e.g. `"minecraft:barrier"`. */
    data class Block(
        val pos: BlockPos3,
        val blockId: String,
        override val point: Vec3d,
        override val distanceSq: Double,
        val face: BlockFace = BlockFace.UP,
    ) : RaycastHit

    data class Entity(
        val entity: EntitySnapshot,
        override val point: Vec3d,
        override val distanceSq: Double,
    ) : RaycastHit
}

/** Offsets point to the adjacent block. */
enum class BlockFace(val offsetX: Int, val offsetY: Int, val offsetZ: Int) {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0),
}

/** For callers that also hit local-only geometry. */
data class CameraRay(val start: Vec3d, val end: Vec3d)

interface RaycastHost {
    /** Nearest hit within [maxDistance], or null. Entities only with [includeEntities]. */
    fun lookingAt(maxDistance: Double, includeEntities: Boolean = true): RaycastHit?

    /** From the camera controller's viewpoint when one owns the camera. */
    fun lookingAtCamera(maxDistance: Double, includeEntities: Boolean = true): RaycastHit? =
        lookingAt(maxDistance, includeEntities)

    /** Null without a world/player. */
    fun cameraRay(maxDistance: Double): CameraRay? = null

    /** First block hit on the way to [target] - "could I click that point" before turning. */
    fun blockToward(target: Vec3d, maxDistance: Double): RaycastHit.Block? = null
}
