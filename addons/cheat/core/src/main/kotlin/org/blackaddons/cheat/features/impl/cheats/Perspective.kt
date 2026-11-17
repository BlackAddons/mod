package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.camera.CameraController
import org.blackaddons.api.camera.CameraControllers
import org.blackaddons.api.camera.CameraPerspective
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.input.MouseScrollEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature

object Perspective : Feature(
    description = "Allows looking around in a 360 degree third-person view without changing your heading.",
    name = "Perspective",
), CameraController {

    override val section = Categories.Cheats.Camera
    private val toggleKey by keybind("Hotkey")
        .withDescription("Press/hold to activate Perspective mode.")
    private val holdMode by boolean("Hold Mode", default = false)
        .withDescription("Active only while the hotkey is held down.")
    private val sensitivity by number("Sensitivity", default = 1.0, min = 0.1, max = 5.0, step = 0.1)
        .withDescription("Looking sensitivity speed multiplier.")
    val distance by number("Distance", default = 4.0, min = 1.0, max = 20.0, step = 0.5)
        .withDescription("Camera distance in blocks.")
    private val scrollEnabled by boolean("Scroll Zoom", default = true)
        .withDescription("Allows zooming with the mouse scroll wheel.")

    // Mouse-driven and applied right away like vanilla's turn. Interpolating left the camera trailing behind.
    private var yaw = 0f
    private var pitch = 0f

    private var previousPerspective: CameraPerspective? = null

    override val active: Boolean get() = enabled
    override val pullback: Float get() = distance.value.toFloat()
    override val hideHands: Boolean get() = false

    override fun init() {
        toggleKey(toggleKey, holdMode)

        register<WorldChangeEvent> {
            if (enabled) toggle()
        }

        register<MouseScrollEvent> {
            if (enabled && scrollEnabled.value && !Platform.screenHost.anyScreenOpen) {
                distance.value -= event.yOffset * distance.step
                event.cancelled = true
            }
        }
    }

    override fun onEnable() {
        super.onEnable()
        if (!Platform.player.isInGame) {
            toggle()
            return
        }

        previousPerspective = Platform.camera.perspective
        Platform.camera.perspective = CameraPerspective.THIRD_PERSON_BACK

        yaw = Platform.player.yaw
        pitch = Platform.player.pitch

        CameraControllers.register(this)
    }

    override fun onDisable() {
        super.onDisable()
        CameraControllers.release(this)
        val prev = previousPerspective
        if (prev != null) {
            Platform.camera.perspective = prev
            previousPerspective = null
        }
    }

    override fun onDisplaced() {
        if (enabled) toggle()
    }

    override fun onTurn(deltaX: Double, deltaY: Double) {
        yaw += (deltaX * 0.15f * sensitivity.value).toFloat()
        pitch += (deltaY * 0.15f * sensitivity.value).toFloat()
        pitch = pitch.coerceIn(-90.0f, 90.0f)
    }

    override fun posX(tickDelta: Float): Double = 0.0
    override fun posY(tickDelta: Float): Double = 0.0
    override fun posZ(tickDelta: Float): Double = 0.0
    override fun rotYaw(tickDelta: Float): Float = yaw
    override fun rotPitch(tickDelta: Float): Float = pitch
}
