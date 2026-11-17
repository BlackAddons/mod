package org.blackaddons.cheat.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.core.features.impl.settings.Actions
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * While No Rotate keeps the server yaw away from the camera, this presses the keys that walk (at the server yaw) in the
 * direction the player's keys point from the camera. Sprint is left to the game. It runs last, because a drive that
 * moves the player replaces these keys anyway and already aims at the server yaw.
 */
internal object FixedNoRotateMovement : FeatureExtension(Actions) {

    private val fixedMovement by boolean(name = "Fixed NoRotate Movement", default = true)
        .withDescription("While NoRotate turns you, your movement keys keep moving you where the camera faces.")

    override fun init() {
        register<MovementInputEvent>(EventPriority.LOWEST) {
            if (!fixedMovement.value) return@register
            val serverYaw = Platform.movement.silentYaw ?: return@register
            val (forward, left) = keysAtServerYaw(event.forwardImpulse, event.leftImpulse, Platform.player.yaw, serverYaw)
                ?: return@register
            event.forward = forward > 0
            event.backward = forward < 0
            event.left = left > 0
            event.right = left < 0
            event.forwardImpulse = forward.toFloat()
            event.leftImpulse = left.toFloat()
        }
    }
}

/**
 * Forward/left keys (-1, 0, 1) at [serverYaw] closest to [forward]/[left] at [cameraYaw], null when nothing is pressed.
 * Yaw turns clockwise.
 */
internal fun keysAtServerYaw(forward: Float, left: Float, cameraYaw: Float, serverYaw: Float): Pair<Int, Int>? {
    if (forward == 0f && left == 0f) return null
    val angle = Math.toDegrees(atan2(left.toDouble(), forward.toDouble())) + serverYaw - cameraYaw
    val octant = Math.floorMod((angle / 45.0).roundToInt(), 8)
    val snapped = Math.toRadians(octant * 45.0)
    return cos(snapped).roundToInt() to sin(snapped).roundToInt()
}
