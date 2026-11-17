package org.blackaddons.api.camera

/**
 * A feature that takes over the camera (freecam, perspective). The camera mixin reads
 * [CameraControllers.active] each frame, so the platform never references a feature.
 */
interface CameraController {
    val active: Boolean

    fun posX(tickDelta: Float): Double
    fun posY(tickDelta: Float): Double
    fun posZ(tickDelta: Float): Double
    fun rotYaw(tickDelta: Float): Float
    fun rotPitch(tickDelta: Float): Float

    /** >0 anchors at the eye and pulls back this many blocks; 0 uses [posX]/[posY]/[posZ]. */
    val pullback: Float

    val hideHands: Boolean

    /** Vanilla turning is cancelled while a controller is active. */
    fun onTurn(deltaX: Double, deltaY: Double)

    /** Another controller took the camera: switch the feature off rather than wait. */
    fun onDisplaced()
}

/** One camera holder at a time; taking it displaces the previous one via [CameraController.onDisplaced]. */
object CameraControllers {
    @Volatile
    private var holder: CameraController? = null

    @JvmStatic
    fun register(controller: CameraController) {
        val previous = holder
        holder = controller
        // After the swap, so the displaced one switching off can't clear the newcomer.
        if (previous != null && previous !== controller) previous.onDisplaced()
    }

    @JvmStatic
    fun release(controller: CameraController) {
        if (holder === controller) holder = null
    }

    @JvmStatic
    fun active(): CameraController? = holder?.takeIf { it.active }
}
