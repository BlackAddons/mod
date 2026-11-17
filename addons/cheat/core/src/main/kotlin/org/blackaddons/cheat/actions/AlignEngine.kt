package org.blackaddons.cheat.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.world.Vec3d
import org.blackaddons.api.world.ViewAngles
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Runs an ALIGN step. The math lives in [AlignSolver] so we can test it. */
object AlignEngine {
    private const val ALIGN_TOLERANCE_BLOCKS = 0.012 // 12 mm
    private const val SNAP_MOVEMENT_LOCK_TICKS = 2
    private const val MAX_ALIGN_ATTEMPTS = 2
    private const val BASE_AIR_DRAG = 0.91f
    private const val MOVEMENT_SPEED_FACTOR = 0.21600002f
    private const val MOVEMENT_INPUT_SCALE = 0.98f

    var active: Boolean = false
        private set

    var targetX: Double = 0.0
        internal set
    var targetZ: Double = 0.0
        internal set

    private var startTimeMs: Long = 0L
    private var currentTimeoutMs: Long = 5000L
    private var finishWhileMoving: Boolean = false

    var attemptsCount: Int = 0
        private set

    private var driftWaitTicks: Int = 0

    private var doLookAfter: Boolean = false
    internal var shouldSnapVisualAfterCompletion: Boolean = false
        private set
    private var alignPostYaw: Float = 0f
    private var alignPostPitch: Float = 0f
    private var alignUseLookAfterCoords: Boolean = false
    private var alignLookAtX: Double = 0.0
    private var alignLookAtY: Double = 0.0
    private var alignLookAtZ: Double = 0.0

    private var alignState: Int = 0
    private var yaw1: Float = 0f
    private var yaw2: Float = 0f
    private var tick1PlannedAWalk: Double = 0.0
    private var tick1PlannedF: Double = 0.0
    private var movementLockTicks: Int = 0
    private var debugTotalTicks: Int = 0
    private var lastSilentYaw: Float? = null

    var forcedForward: Boolean = false
        private set
    var forcedBackward: Boolean = false
        private set
    var forcedLeft: Boolean = false
        private set
    var forcedRight: Boolean = false
        private set
    var forcedSneak: Boolean = false
        private set

    val shouldBlockMovementInput: Boolean
        get() = active || movementLockTicks > 0

    fun start(
        x: Double,
        z: Double,
        timeoutMs: Long,
        lookAfter: Boolean,
        useLookAfterCoords: Boolean,
        postYaw: Float,
        postPitch: Float,
        lookAtX: Double,
        lookAtY: Double,
        lookAtZ: Double,
        dontWaitForPerfectAlign: Boolean = false,
    ) {
        targetX = x
        targetZ = z
        currentTimeoutMs = timeoutMs
        doLookAfter = lookAfter
        shouldSnapVisualAfterCompletion = false
        alignPostYaw = postYaw
        alignPostPitch = postPitch
        alignUseLookAfterCoords = useLookAfterCoords
        alignLookAtX = lookAtX
        alignLookAtY = lookAtY
        alignLookAtZ = lookAtZ
        finishWhileMoving = dontWaitForPerfectAlign
        Platform.movement.setSprintBlocked(true)

        if (!active) {
            clearForcedMovement()
            active = true
            alignState = 0
            attemptsCount = 0
            driftWaitTicks = 0
            startTimeMs = System.currentTimeMillis()
            debugTotalTicks = 0

            val p = Platform.player
            AlignDebugger.onStart(
                targetX, targetZ, p.x, p.y, p.z,
                Platform.movement.velocityX, Platform.movement.velocityZ, Platform.movement.isCrouching
            )
        } else {
            alignState = 0
        }
    }

    fun cancel() {
        Platform.movement.setSprintBlocked(false)
        shouldSnapVisualAfterCompletion = false
        if (!active) return
        active = false
        movementLockTicks = 0
        lastSilentYaw = null
        clearForcedMovement()
        AlignDebugger.onCancel(debugTotalTicks, attemptsCount)
    }

    fun tick() {
        if (movementLockTicks > 0) movementLockTicks--
        if (!active) return

        val player = Platform.player
        if (!player.isInGame || Platform.movement.isScreenOpen) {
            cancel()
            return
        }

        val movementHost = Platform.movement
        if (movementHost.isPassenger || movementHost.isFallFlying || movementHost.isClimbing ||
            movementHost.isInFluid || movementHost.isItemUseChangingSpeed
        ) {
            cancel()
            return
        }

        if (System.currentTimeMillis() - startTimeMs > currentTimeoutMs) {
            cancel()
            return
        }

        debugTotalTicks++
        AlignDebugger.onTick(player.x, player.y, player.z)

        if (alignState == 0) {
            if (!player.onGround) return
            if (movementHost.isSprinting) {
                movementHost.stopSprinting()
                return
            }

            val blockFriction = AlignSolver.modifiedFriction(movementHost.surfaceSlipperiness, movementHost.frictionModifier)
            val airDrag = AlignSolver.modifiedFriction(BASE_AIR_DRAG, movementHost.airDragModifier)
            val f = blockFriction * airDrag

            val frictionSpeed = if (blockFriction.toDouble() > 0.6) {
                movementHost.movementSpeed * (MOVEMENT_SPEED_FACTOR / (blockFriction * blockFriction * blockFriction))
            } else {
                movementHost.movementSpeed
            }
            val aWalk = frictionSpeed.toDouble() * MOVEMENT_INPUT_SCALE.toDouble()

            val vx = movementHost.velocityX
            val vz = movementHost.velocityZ

            val (driftX, driftZ) = AlignSolver.driftVector(vx, vz, f.toDouble())
            val predX = player.x + driftX
            val predZ = player.z + driftZ

            val rx = targetX - predX
            val rz = targetZ - predZ
            val dist = hypot(rx, rz)
            val phi = AlignSolver.wrapDegrees(Math.toDegrees(atan2(rz, rx)) - 90.0)

            if (finishWhileMoving && dist <= ALIGN_TOLERANCE_BLOCKS) {
                finish()
                return
            }

            if (dist <= ALIGN_TOLERANCE_BLOCKS) {
                if (vx * vx + vz * vz < AlignSolver.MIN_MOVEMENT_DISTANCE_SQR) {
                    finish()
                    return
                }
                clearForcedMovement()
                alignState = 4
                driftWaitTicks = 3
                return
            }

            val dWalk = AlignSolver.driftOf(aWalk, f.toDouble())
            val max2Walk = dWalk * (1.0 + f.toDouble())

            val solution = AlignSolver.solveSnap(
                player.x, player.z, vx, vz,
                targetX, targetZ, aWalk, f.toDouble()
            )

            if (dist > max2Walk - ALIGN_TOLERANCE_BLOCKS || (solution.error > ALIGN_TOLERANCE_BLOCKS && dist > 0.2)) {
                AlignDebugger.updateApproachingPredictedPath(player.x, player.y, player.z, targetX, targetZ)
                applyMovement(phi.toFloat())
                return
            }

            if (attemptsCount >= MAX_ALIGN_ATTEMPTS) {
                AlignDebugger.onFailMaxAttempts(MAX_ALIGN_ATTEMPTS)
                finish()
                return
            }

            attemptsCount++
            yaw1 = solution.yaw1
            yaw2 = solution.yaw2
            tick1PlannedAWalk = aWalk
            tick1PlannedF = f.toDouble()
            driftWaitTicks = 3

            val predictedPath = AlignSolver.predictedPath(
                player.x, player.z, player.y, vx, vz,
                aWalk, f.toDouble(), yaw1, yaw2
            )
            AlignDebugger.onSnapSolved(predictedPath, attemptsCount, MAX_ALIGN_ATTEMPTS, yaw1, false, yaw2, false, solution.error * 1000.0)

            alignState = 1
        }

        if (alignState == 1) {
            alignState = if (yaw2.isNaN()) 3 else 2
            applyMovement(yaw1)
        } else if (alignState == 2) {
            yaw2 = AlignSolver.correctSecondYaw(
                realX = player.x,
                realZ = player.z,
                realVx = movementHost.velocityX,
                realVz = movementHost.velocityZ,
                targetX = targetX,
                targetZ = targetZ,
                aWalk = tick1PlannedAWalk,
                f = tick1PlannedF,
                fallbackYaw = yaw2
            )
            alignState = 3
            applyMovement(yaw2)
        } else if (alignState == 3) {
            clearForcedMovement()
            if (finishWhileMoving && predictedRestDistance(tick1PlannedF) <= ALIGN_TOLERANCE_BLOCKS) {
                finish()
                return
            }
            alignState = 4
        } else if (alignState == 4) {
            clearForcedMovement()
            val currentDist = hypot(targetX - player.x, targetZ - player.z)
            if (driftWaitTicks > 0) {
                driftWaitTicks--
                return
            }

            val vx = movementHost.velocityX
            val vz = movementHost.velocityZ
            if (vx * vx + vz * vz < AlignSolver.MIN_MOVEMENT_DISTANCE_SQR) {
                if (currentDist <= ALIGN_TOLERANCE_BLOCKS) {
                    finish()
                } else if (attemptsCount < MAX_ALIGN_ATTEMPTS) {
                    alignState = 0
                } else {
                    if (currentDist > ALIGN_TOLERANCE_BLOCKS) {
                        AlignDebugger.onFailMaxAttempts(MAX_ALIGN_ATTEMPTS)
                    }
                    finish()
                }
            }
        }
    }

    private fun predictedRestDistance(friction: Double): Double {
        val movement = Platform.movement
        val (driftX, driftZ) = AlignSolver.driftVector(movement.velocityX, movement.velocityZ, friction)
        return hypot(targetX - (Platform.player.x + driftX), targetZ - (Platform.player.z + driftZ))
    }

    private data class MovementCandidate(
        val lookYaw: Float,
        val forward: Boolean = false,
        val backward: Boolean = false,
        val left: Boolean = false,
        val right: Boolean = false,
    )

    private fun applyMovement(targetYaw: Float) {
        val baseYaw = lastSilentYaw ?: Platform.movement.serverYaw

        if (AlignSettings.isSilentAlignDisabled) {
            applyMovementCandidate(MovementCandidate(targetYaw, forward = true), baseYaw, silent = false)
            return
        }

        val candidates = if (AlignSettings.isMinimizeYawSnapEnabled) {
            listOf(
                MovementCandidate(targetYaw, forward = true),
                MovementCandidate(targetYaw + 90f, left = true),
                MovementCandidate(targetYaw - 90f, right = true),
                MovementCandidate(targetYaw + 180f, backward = true),
            )
        } else {
            listOf(MovementCandidate(targetYaw, forward = true))
        }
        val best = candidates.minBy { angularDistance(it.lookYaw, baseYaw) }
        applyMovementCandidate(best, baseYaw, silent = true)
    }

    private fun applyMovementCandidate(candidate: MovementCandidate, baseYaw: Float, silent: Boolean) {
        val unwrappedYaw = ViewAngles.unwrap(candidate.lookYaw, near = baseYaw)
        lastSilentYaw = unwrappedYaw

        ActionAim.lease(silent).set(unwrappedYaw, Platform.movement.serverPitch)

        forcedForward = candidate.forward
        forcedBackward = candidate.backward
        forcedLeft = candidate.left
        forcedRight = candidate.right
        forcedSneak = false
    }

    private fun angularDistance(a: Float, b: Float): Float =
        abs(ViewAngles.yawDelta(b, a))

    private fun finish() {
        val player = Platform.player
        AlignDebugger.onFinish(targetX, targetZ, player.x, player.z, attemptsCount, MAX_ALIGN_ATTEMPTS, debugTotalTicks, startTimeMs)

        shouldSnapVisualAfterCompletion = false
        active = false
        Platform.movement.setSprintBlocked(false)
        lastSilentYaw = null
        clearForcedMovement()
        movementLockTicks = SNAP_MOVEMENT_LOCK_TICKS

        if (doLookAfter) {
            shouldSnapVisualAfterCompletion = true
            val (yaw, pitch) = if (alignUseLookAfterCoords) {
                ViewAngles.between(Platform.player.eyePos, Vec3d(alignLookAtX, alignLookAtY, alignLookAtZ))
            } else {
                alignPostYaw to alignPostPitch
            }
            ActionAim.lease().set(yaw, pitch)
        }
    }

    private fun clearForcedMovement() {
        forcedForward = false
        forcedBackward = false
        forcedLeft = false
        forcedRight = false
        forcedSneak = false
    }
}
