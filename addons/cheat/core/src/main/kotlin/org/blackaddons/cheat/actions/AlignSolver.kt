package org.blackaddons.cheat.actions

import org.blackaddons.api.world.Vec3d
import kotlin.math.IEEErem
import kotlin.math.hypot
import kotlin.math.sin

/**
 * ALIGN math: the one or two yaws that reach the target. It's pure, so it runs in tests without Minecraft. The sine
 * table and drag have to match vanilla bit for bit.
 */
internal object AlignSolver {

    /** Below this squared speed vanilla just zeroes X/Z velocity (LivingEntity.aiStep). */
    const val MIN_MOVEMENT_DISTANCE_SQR = 9.0E-6

    private const val SIN_TABLE_SIZE = 65536
    private const val SIN_MASK = 65535
    private const val COS_OFFSET = 16384
    private const val SIN_SCALE = 10430.378350470453
    private const val DEG_TO_RAD_F = 0.017453292f

    // Coarse grid first, then halving refinement around the best cell.
    private const val GRID_SIZE = 36
    private const val REFINE_ITERATIONS = 30
    private const val INITIAL_STEP_SIZE = 10.0

    private val SIN_TABLE = FloatArray(SIN_TABLE_SIZE) { i ->
        sin(i.toDouble() / SIN_SCALE).toFloat()
    }

    fun predictedPath(
        sx: Double, sz: Double, sy: Double,
        vx: Double, vz: Double,
        aWalk: Double, f: Double,
        yaw1: Float, yaw2: Float,
    ): ArrayList<Vec3d> {
        val list = ArrayList<Vec3d>()
        list.add(Vec3d(sx, sy, sz))

        var v1x = vx
        var v1z = vz
        if (v1x * v1x + v1z * v1z < MIN_MOVEMENT_DISTANCE_SQR) {
            v1x = 0.0
            v1z = 0.0
        }
        v1x += aWalk * yawUnitX(yaw1)
        v1z += aWalk * yawUnitZ(yaw1)
        val x1 = sx + v1x
        val z1 = sz + v1z
        list.add(Vec3d(x1, sy, z1))

        if (yaw2.isNaN()) {
            val (driftX, driftZ) = driftVector(v1x * f, v1z * f, f)
            list.add(Vec3d(x1 + driftX, sy, z1 + driftZ))
            return list
        }

        var v2x = v1x * f
        var v2z = v1z * f
        if (v2x * v2x + v2z * v2z < MIN_MOVEMENT_DISTANCE_SQR) {
            v2x = 0.0
            v2z = 0.0
        }
        v2x += aWalk * yawUnitX(yaw2)
        v2z += aWalk * yawUnitZ(yaw2)
        val x2 = x1 + v2x
        val z2 = z1 + v2z
        list.add(Vec3d(x2, sy, z2))

        val (driftX, driftZ) = driftVector(v2x * f, v2z * f, f)
        list.add(Vec3d(x2 + driftX, sy, z2 + driftZ))

        return list
    }

    data class SnapSolution(val yaw1: Float, val yaw2: Float, val error: Double)

    private data class YawPairResult(val yaw1: Float, val yaw2: Float, val error: Double)

    private data class YawResult(val yaw: Float, val error: Double)

    fun solveSnap(
        sx: Double, sz: Double, vx: Double, vz: Double,
        tx: Double, tz: Double, aWalk: Double, f: Double,
    ): SnapSolution {
        val pair = solveYawPair(sx, sz, vx, vz, tx, tz, aWalk, aWalk, f)
        val single = solveSingleYaw(sx, sz, vx, vz, tx, tz, aWalk, f)
        return if (single.error < pair.error) {
            SnapSolution(single.yaw, Float.NaN, single.error)
        } else {
            SnapSolution(pair.yaw1, pair.yaw2, pair.error)
        }
    }

    private fun solveYawPair(
        sx: Double, sz: Double, vx: Double, vz: Double,
        tx: Double, tz: Double, a1: Double, a2: Double, f: Double,
    ): YawPairResult {
        val stepGrid = 360.0 / GRID_SIZE
        var bestErr = Double.MAX_VALUE
        var bestY1 = 0.0
        var bestY2 = 0.0

        for (i in 0 until GRID_SIZE) {
            val g1 = -180.0 + i * stepGrid
            for (j in 0 until GRID_SIZE) {
                val g2 = -180.0 + j * stepGrid
                val err = evalYawError(sx, sz, vx, vz, a1, a2, f, g1.toFloat(), g2.toFloat(), tx, tz)
                if (err < bestErr) {
                    bestErr = err
                    bestY1 = g1
                    bestY2 = g2
                }
            }
        }

        var stepSize = INITIAL_STEP_SIZE
        for (iter in 0 until REFINE_ITERATIONS) {
            var improved = false
            var candY1 = bestY1
            var candY2 = bestY2

            val scale = stepSize / INITIAL_STEP_SIZE
            for (d1 in doubleArrayOf(-INITIAL_STEP_SIZE, 0.0, INITIAL_STEP_SIZE)) {
                for (d2 in doubleArrayOf(-INITIAL_STEP_SIZE, 0.0, INITIAL_STEP_SIZE)) {
                    if (d1 == 0.0 && d2 == 0.0) continue
                    val y1c = wrapDegrees(bestY1 + d1 * scale)
                    val y2c = wrapDegrees(bestY2 + d2 * scale)
                    val err = evalYawError(sx, sz, vx, vz, a1, a2, f, y1c.toFloat(), y2c.toFloat(), tx, tz)
                    if (err < bestErr) {
                        bestErr = err
                        candY1 = y1c
                        candY2 = y2c
                        improved = true
                    }
                }
            }

            if (improved) {
                bestY1 = candY1
                bestY2 = candY2
            }

            stepSize *= 0.5
            if (stepSize < 1e-6 || bestErr < 1e-16) break
        }

        return YawPairResult(wrapDegrees(bestY1).toFloat(), wrapDegrees(bestY2).toFloat(), bestErr)
    }

    fun correctSecondYaw(
        realX: Double, realZ: Double,
        realVx: Double, realVz: Double,
        targetX: Double, targetZ: Double,
        aWalk: Double, f: Double,
        fallbackYaw: Float,
    ): Float {
        val corrected = solveSingleYaw(realX, realZ, realVx, realVz, targetX, targetZ, aWalk, f)
        val fallbackError = evalYawError(
            realX, realZ, realVx, realVz,
            aWalk, 0.0, f, fallbackYaw, null,
            targetX, targetZ,
        )
        return if (corrected.error < fallbackError) corrected.yaw else fallbackYaw
    }

    private fun solveSingleYaw(
        sx: Double, sz: Double, vx: Double, vz: Double,
        tx: Double, tz: Double, acceleration: Double, friction: Double,
    ): YawResult {
        val stepGrid = 360.0 / GRID_SIZE
        var bestYaw = 0.0
        var bestErr = Double.MAX_VALUE

        for (i in 0 until GRID_SIZE) {
            val g = -180.0 + i * stepGrid
            val err = evalYawError(sx, sz, vx, vz, acceleration, 0.0, friction, g.toFloat(), null, tx, tz)
            if (err < bestErr) {
                bestErr = err
                bestYaw = g
            }
        }

        var stepSize = INITIAL_STEP_SIZE
        for (iter in 0 until REFINE_ITERATIONS) {
            var improved = false
            var candYaw = bestYaw
            val scale = stepSize / INITIAL_STEP_SIZE
            for (d in doubleArrayOf(-INITIAL_STEP_SIZE, INITIAL_STEP_SIZE)) {
                val yc = wrapDegrees(bestYaw + d * scale)
                val err = evalYawError(sx, sz, vx, vz, acceleration, 0.0, friction, yc.toFloat(), null, tx, tz)
                if (err < bestErr) {
                    bestErr = err
                    candYaw = yc
                    improved = true
                }
            }
            if (improved) bestYaw = candYaw
            stepSize *= 0.5
            if (stepSize < 1e-6 || bestErr < 1e-16) break
        }

        return YawResult(wrapDegrees(bestYaw).toFloat(), bestErr)
    }

    private fun evalYawError(
        startX: Double, startZ: Double,
        vx: Double, vz: Double,
        a1: Double, a2: Double, f: Double,
        yaw1: Float, yaw2: Float?,
        targetX: Double, targetZ: Double,
    ): Double {
        var v1x = vx
        var v1z = vz
        if (v1x * v1x + v1z * v1z < MIN_MOVEMENT_DISTANCE_SQR) {
            v1x = 0.0
            v1z = 0.0
        }
        v1x += a1 * yawUnitX(yaw1)
        v1z += a1 * yawUnitZ(yaw1)
        val x1 = startX + v1x
        val z1 = startZ + v1z

        if (yaw2 == null) {
            var curVx = v1x * f
            var curVz = v1z * f
            var driftX = 0.0
            var driftZ = 0.0
            while (curVx * curVx + curVz * curVz >= MIN_MOVEMENT_DISTANCE_SQR) {
                driftX += curVx
                driftZ += curVz
                curVx *= f
                curVz *= f
            }
            val dx = x1 + driftX - targetX
            val dz = z1 + driftZ - targetZ
            return hypot(dx, dz)
        }

        var v2x = v1x * f
        var v2z = v1z * f
        if (v2x * v2x + v2z * v2z < MIN_MOVEMENT_DISTANCE_SQR) {
            v2x = 0.0
            v2z = 0.0
        }
        v2x += a2 * yawUnitX(yaw2)
        v2z += a2 * yawUnitZ(yaw2)
        val x2 = x1 + v2x
        val z2 = z1 + v2z

        var curVx = v2x * f
        var curVz = v2z * f
        var driftX = 0.0
        var driftZ = 0.0
        while (curVx * curVx + curVz * curVz >= MIN_MOVEMENT_DISTANCE_SQR) {
            driftX += curVx
            driftZ += curVz
            curVx *= f
            curVz *= f
        }
        val dx = x2 + driftX - targetX
        val dz = z2 + driftZ - targetZ
        return hypot(dx, dz)
    }

    fun driftOf(velocity: Double, friction: Double): Double {
        var drift = 0.0
        var curVx = velocity
        while (curVx * curVx >= MIN_MOVEMENT_DISTANCE_SQR) {
            drift += curVx
            curVx *= friction
        }
        return drift
    }

    fun driftVector(vx: Double, vz: Double, friction: Double): Pair<Double, Double> {
        var curVx = vx
        var curVz = vz
        var driftX = 0.0
        var driftZ = 0.0
        while (curVx * curVx + curVz * curVz >= MIN_MOVEMENT_DISTANCE_SQR) {
            driftX += curVx
            driftZ += curVz
            curVx *= friction
            curVz *= friction
        }
        return Pair(driftX, driftZ)
    }

    private fun yawUnitX(yaw: Float): Double {
        val radians = (yaw * DEG_TO_RAD_F).toDouble()
        val idx = (radians * SIN_SCALE).toLong() and SIN_MASK.toLong()
        return -SIN_TABLE[idx.toInt()].toDouble()
    }

    private fun yawUnitZ(yaw: Float): Double {
        val radians = (yaw * DEG_TO_RAD_F).toDouble()
        val idx = (radians * SIN_SCALE + COS_OFFSET).toLong() and SIN_MASK.toLong()
        return SIN_TABLE[idx.toInt()].toDouble()
    }

    fun modifiedFriction(friction: Float, modifier: Float): Float =
        (1f - (1f - friction) * modifier).coerceIn(0f, 1f)

    fun wrapDegrees(degrees: Double): Double = degrees.IEEErem(360.0)
}
