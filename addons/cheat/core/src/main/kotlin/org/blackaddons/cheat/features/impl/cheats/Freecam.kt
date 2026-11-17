package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.camera.CameraController
import org.blackaddons.api.camera.CameraControllers
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Freecam : Feature(
    description = "Allows you to fly out of your body to inspect the world.",
    name = "Freecam",
), CameraController {

    override val section = Categories.Cheats.Camera
    private val toggleKey by keybind("Hotkey")
        .withDescription("Press/hold to activate Freecam mode.")
    private val holdMode by boolean("Hold Mode", default = false)
        .withDescription("Active only while the hotkey is held down.")
    private val speedSetting by number("Speed", default = 1.0, min = 0.1, max = 5.0, step = 0.1)
        .withDescription("Flight movement speed multiplier.")
    val showHands by boolean("Show Hands", default = false)
        .withDescription("Whether to render player hands while in Freecam.")

    private const val MOVEMENT_MULTIPLIER = 0.5

    private val pos = Vector3d()
    private val prevPos = Vector3d()

    // Mouse-driven and applied right away like vanilla's turn. Per-tick interpolation ate part of each movement.
    private var yaw = 0f
    private var pitch = 0f

    private var lastDimension: String? = null
    private var lastPlayerUuid: UUID? = null
    private var lastInput: InputState? = null

    private class InputState(
        val forward: Boolean,
        val backward: Boolean,
        val left: Boolean,
        val right: Boolean,
        val jump: Boolean,
        val shift: Boolean,
        val sprint: Boolean,
    )

    override val active: Boolean get() = enabled
    override val pullback: Float get() = 0f
    override val hideHands: Boolean get() = !showHands.value

    override fun init() {
        toggleKey(toggleKey, holdMode)
        register<ClientTickEvent.Post> { onTick() }

        register<WorldChangeEvent> {
            if (enabled) toggle()
        }

        register<MovementInputEvent>(EventPriority.HIGHEST) {
            if (enabled) {
                lastInput = InputState(
                    event.forward,
                    event.backward,
                    event.left,
                    event.right,
                    event.jump,
                    event.shift,
                    event.sprint,
                )
                event.release()
            }
        }
    }

    override fun onEnable() {
        super.onEnable()
        if (!Platform.player.isInGame) {
            toggle()
            return
        }

        yaw = Platform.player.yaw
        pitch = Platform.player.pitch
        lastPlayerUuid = Platform.player.uuid
        lastDimension = Platform.player.dimensionId

        val camX = Platform.camera.eyeX
        val camY = Platform.camera.eyeY
        val camZ = Platform.camera.eyeZ
        pos.set(camX, camY, camZ)
        prevPos.set(camX, camY, camZ)

        CameraControllers.register(this)
    }

    override fun onDisable() {
        super.onDisable()
        CameraControllers.release(this)
        lastInput = null
    }

    private fun onTick() {
        if (!Platform.player.isInGame ||
            (lastPlayerUuid != null && Platform.player.uuid != lastPlayerUuid) ||
            (lastDimension != null && lastDimension != Platform.player.dimensionId)
        ) {
            if (enabled) toggle()
            return
        }

        prevPos.set(pos)
        // Consumed each tick: no input arrives behind a screen or while paused, and a held key shouldn't keep flying.
        val input = lastInput ?: return
        lastInput = null
        if (Platform.screenHost.anyScreenOpen) return

        val radYaw = Math.toRadians(yaw.toDouble())
        val forwardX = -sin(radYaw)
        val forwardZ = cos(radYaw)
        val rightX = -forwardZ
        val rightZ = forwardX

        var speed = speedSetting.value * MOVEMENT_MULTIPLIER
        if (input.sprint) speed *= 2.0

        var velX = 0.0
        var velY = 0.0
        var velZ = 0.0

        var movingInPlane = false
        if (input.forward) {
            velX += forwardX * speed
            velZ += forwardZ * speed
            movingInPlane = true
        }
        if (input.backward) {
            velX -= forwardX * speed
            velZ -= forwardZ * speed
            movingInPlane = true
        }

        var strafing = false
        if (input.right) {
            velX += rightX * speed
            velZ += rightZ * speed
            strafing = true
        }
        if (input.left) {
            velX -= rightX * speed
            velZ -= rightZ * speed
            strafing = true
        }

        if (movingInPlane && strafing) {
            val diagonal = 1.0 / sqrt(2.0)
            velX *= diagonal
            velZ *= diagonal
        }

        if (input.jump) velY += speed
        if (input.shift) velY -= speed

        pos.add(velX, velY, velZ)
    }

    override fun onDisplaced() {
        if (enabled) toggle()
    }

    override fun onTurn(deltaX: Double, deltaY: Double) {
        yaw += (deltaX * 0.15f).toFloat()
        pitch += (deltaY * 0.15f).toFloat()
        pitch = pitch.coerceIn(-90.0f, 90.0f)
    }

    override fun posX(tickDelta: Float): Double = prevPos.x + (pos.x - prevPos.x) * tickDelta
    override fun posY(tickDelta: Float): Double = prevPos.y + (pos.y - prevPos.y) * tickDelta
    override fun posZ(tickDelta: Float): Double = prevPos.z + (pos.z - prevPos.z) * tickDelta
    override fun rotYaw(tickDelta: Float): Float = yaw
    override fun rotPitch(tickDelta: Float): Float = pitch
}
