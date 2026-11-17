package org.blackaddons.cheat.actions

import org.blackaddons.api.Platform
import kotlin.math.cos
import kotlin.math.sin

object StopEngine {
    private const val MIN_SPEED_SQ = 0.0001
    private const val DRAG_FACTOR = 0.546000082
    private const val MAX_RUN_TICKS = 10

    var active: Boolean = false
        private set

    var forcedForward: Boolean = false
        private set
    var forcedBackward: Boolean = false
        private set
    var forcedLeft: Boolean = false
        private set
    var forcedRight: Boolean = false
        private set

    private var runTicks: Int = 0
    private var lockTicks: Int = 0

    val shouldBlockMovementInput: Boolean
        get() = active || lockTicks > 0

    fun start() {
        active = true
        clearForcedInputs()
        runTicks = 0
    }

    fun cancel() {
        if (!active) return
        active = false
        clearForcedInputs()
        runTicks = 0
        lockTicks = 0
    }

    fun tick() {
        if (lockTicks > 0) lockTicks--
        if (!active) return

        val player = Platform.player
        val movementHost = Platform.movement

        if (!player.isInGame || movementHost.isScreenOpen || movementHost.isPassenger ||
            movementHost.isFallFlying || movementHost.isClimbing || movementHost.isInFluid
        ) {
            cancel()
            return
        }

        runTicks++
        if (runTicks > MAX_RUN_TICKS) {
            finish()
            return
        }

        val vx = movementHost.velocityX
        val vz = movementHost.velocityZ
        val speedSq = vx * vx + vz * vz

        if (speedSq < MIN_SPEED_SQ) {
            finish()
            return
        }

        if (movementHost.isSprinting) {
            movementHost.stopSprinting()
        }

        val yawRad = Math.toRadians(movementHost.serverYaw.toDouble())
        val fwdX = -sin(yawRad)
        val fwdZ = cos(yawRad)
        val leftX = cos(yawRad)
        val leftZ = sin(yawRad)

        val fwdDot = vx * fwdX + vz * fwdZ
        val leftDot = vx * leftX + vz * leftZ

        val accel = movementHost.movementSpeed.toDouble() * 0.98
        val baseNextSq = squaredAfterTick(fwdDot, leftDot, 0.0, 0.0)

        val pressFwd = fwdDot < -0.01 && squaredAfterTick(fwdDot, leftDot, accel, 0.0) < baseNextSq
        val pressBack = fwdDot > 0.01 && squaredAfterTick(fwdDot, leftDot, -accel, 0.0) < baseNextSq
        val pressLeft = leftDot < -0.01 && squaredAfterTick(fwdDot, leftDot, 0.0, accel) < baseNextSq
        val pressRight = leftDot > 0.01 && squaredAfterTick(fwdDot, leftDot, 0.0, -accel) < baseNextSq

        if (!pressFwd && !pressBack && !pressLeft && !pressRight) {
            finish()
            return
        }

        forcedForward = pressFwd
        forcedBackward = pressBack
        forcedLeft = pressLeft
        forcedRight = pressRight
    }

    private fun squaredAfterTick(fwd: Double, left: Double, dFwd: Double, dLeft: Double): Double {
        val nf = (fwd + dFwd) * DRAG_FACTOR
        val nl = (left + dLeft) * DRAG_FACTOR
        return nf * nf + nl * nl
    }

    private fun finish() {
        active = false
        clearForcedInputs()
        runTicks = 0
        lockTicks = 1
    }

    private fun clearForcedInputs() {
        forcedForward = false
        forcedBackward = false
        forcedLeft = false
        forcedRight = false
    }
}
