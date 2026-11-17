package org.blackaddons.core.control

import org.blackaddons.api.world.Vec3d

/** What [AimControl] needs from the game. The platform snaps every write to the mouse grid. */
interface ViewPort {
    val inWorld: Boolean
    val screenOpen: Boolean

    val viewYaw: Float
    val viewPitch: Float

    fun setView(yaw: Float, pitch: Float)

    val silentYaw: Float?
    val silentPitch: Float?

    /** Sent to the server instead of the camera rotation. */
    fun setSilent(yaw: Float, pitch: Float)

    fun clearSilent()

    /** The camera moves to the silent rotation, then silent aim stops. */
    fun silentToView()

    val eyePos: Vec3d

    /** Blocks per second, over the last tick. */
    val eyeVelocity: Vec3d

    /** How far into the tick we are, from 0 to 1. The camera renders the eye interpolated by it. */
    val partialTick: Float get() = 1f

    fun anglesTo(point: Vec3d): Pair<Float, Float>

    /** Only moves with the physical mouse, never with the camera. */
    val cursorX: Double
    val cursorY: Double

    /** Degrees per mouse count at the player's sensitivity. */
    val turnStep: Float

    /** Runs inline on the view's thread, otherwise queued. */
    fun execute(block: () -> Unit)
}
