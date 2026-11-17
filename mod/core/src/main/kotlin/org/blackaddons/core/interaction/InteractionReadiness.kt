package org.blackaddons.core.interaction

import org.blackaddons.api.util.near
import org.blackaddons.api.util.nearZero
import org.blackaddons.api.world.ViewAngles
import kotlin.math.abs

class InteractionReadiness {
    data class DeferredClicks(val attack: Boolean, val use: Boolean)

    private var afterPositionSent = true
    private var sentRotation: Pair<Float, Float>? = null
    private var blockUseSent = false
    private var attackDeferred = false
    private var useDeferred = false

    fun onTickStart() { afterPositionSent = false }

    fun onPositionSent(): DeferredClicks {
        afterPositionSent = true
        blockUseSent = false
        val clicks = DeferredClicks(attackDeferred, useDeferred)
        attackDeferred = false
        useDeferred = false
        return clicks
    }

    /** Degrees. 0 before the second rotation. */
    var lastYawChange = 0f
        private set

    fun onRotationSent(yaw: Float, pitch: Float) {
        sentRotation?.let { (lastYaw, _) -> lastYawChange = abs(ViewAngles.yawDelta(lastYaw, yaw)) }
        sentRotation = yaw to pitch
    }

    fun onBlockUseSent() { blockUseSent = true }

    fun deferAttack() { attackDeferred = true }

    fun deferUse() { useDeferred = true }

    fun canUseBlock(): Boolean = !blockUseSent

    fun reset() {
        afterPositionSent = true
        sentRotation = null
        lastYawChange = 0f
        blockUseSent = false
        attackDeferred = false
        useDeferred = false
    }

    /** The rotation the server would check a click sent right now against. Null once this tick's movement went out. */
    fun clickRotation(): Pair<Float, Float>? = sentRotation.takeUnless { afterPositionSent }

    fun canPerform(yaw: Float, pitch: Float, awaitingRotationSync: Boolean): Boolean {
        if (afterPositionSent || awaitingRotationSync) return false
        val (sentYaw, sentPitch) = sentRotation ?: return false
        return ViewAngles.yawDelta(sentYaw, yaw).nearZero(SAME_ANGLE) && sentPitch.near(pitch, SAME_ANGLE)
    }
}

// Covers yaw float drift after a wrap.
private const val SAME_ANGLE = 0.001f
