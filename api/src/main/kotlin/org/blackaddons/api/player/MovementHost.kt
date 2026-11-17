package org.blackaddons.api.player

import org.blackaddons.api.Platform
import org.blackaddons.api.event.input.MovementInputEvent

/** Movement keys that a [MovementHost.drive] can press. */
enum class MoveKey { FORWARD, BACKWARD, LEFT, RIGHT, JUMP, SNEAK, SPRINT }

enum class DrivePriority {
    /** Gives way to every other drive. */
    LOW,

    NORMAL,

    /** Used by actions and Align. */
    HIGH,
}

/**
 * Movement physics and state for prediction, plus who moves the player. It has no Minecraft types. Turning belongs to
 * [RotationHost].
 */
interface MovementHost {
    /** 0.0 when not in game. */
    val velocityX: Double

    /** 0.0 when not in game. */
    val velocityZ: Double

    /** `player.getSpeed()`. */
    val movementSpeed: Float

    /** Block friction under the feet (0.6 default). */
    val surfaceSlipperiness: Float

    val isSprinting: Boolean

    /** Ladder/vine. */
    val isClimbing: Boolean

    /** Water or lava. */
    val isInFluid: Boolean

    /** Elytra gliding. */
    val isFallFlying: Boolean

    val isPassenger: Boolean

    val isCrouching: Boolean

    val isItemUseChangingSpeed: Boolean

    val isScreenOpen: Boolean

    /** Silent yaw sent instead of the camera's, or null. */
    val silentYaw: Float?

    /** Silent pitch sent instead of the camera's, or null. */
    val silentPitch: Float? get() = null

    val serverYaw: Float get() = silentYaw ?: Platform.player.yaw

    val serverPitch: Float get() = silentPitch ?: Platform.player.pitch

    /** Default 1.0. */
    val frictionModifier: Float

    /** Default 1.0. */
    val airDragModifier: Float

    /** Default 0.3. */
    val sneakingSpeed: Float

    fun stopSprinting()

    /** Blocks sprinting while a movement controller needs walking physics. */
    fun setSprintBlocked(blocked: Boolean) {}

    /**
     * Sends sneaking to the server now instead of at the end of the tick, so a click sent after it this tick
     * already counts as sneaking. Keep [org.blackaddons.api.event.input.MovementInputEvent.shift] held from this
     * tick on, or the end of the tick sends the release. The client crouches from the next tick, as with a real key:
     * crouching on this one would move differently from what the server simulates. This tick's movement stays as sent:
     * any other change, from a key or a [org.blackaddons.api.event.input.MovementInputEvent] listener, lands a tick
     * later instead of costing a second input packet in one tick.
     */
    fun sneakNow() {}

    /**
     * Asks to move the player. Every tick the host asks the live drives, strongest first and then oldest first. The
     * first [steer] that answers wins, and its keys replace the player's. Returning null passes this tick on to the
     * next drive or to the player's own keys, and [steer] is not called while a stronger drive steers. It is called on
     * the client thread after every [MovementInputEvent] listener. A newer drive from the same owner replaces the older
     * one, and the drive of an owner that is turned off is revoked.
     */
    fun drive(owner: Any, priority: DrivePriority = DrivePriority.NORMAL, steer: () -> Set<MoveKey>?): Lease

    /** Holds [key] down on top of whatever moves the player. Shared: the key stays down while any owner holds it. */
    fun hold(key: MoveKey, owner: Any): Lease
}
