package org.blackaddons.api.world

import org.blackaddons.api.world.ViewAngles.between
import org.blackaddons.api.world.ViewAngles.unwrap
import org.blackaddons.api.world.ViewAngles.yawDelta
import kotlin.math.IEEErem
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** World-space point, MC-free. */
data class Vec3d(val x: Double, val y: Double, val z: Double) {
    fun distanceSqr(other: Vec3d): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    fun distance(other: Vec3d): Double = sqrt(distanceSqr(other))
}

/** Block position, MC-free. */
data class BlockPos3(val x: Int, val y: Int, val z: Int) {
    fun center() = Vec3d(x + 0.5, y + 0.5, z + 0.5)

    fun distanceSqr(pos: Vec3d): Double = center().distanceSqr(pos)

    fun distance(pos: Vec3d): Double = center().distance(pos)

    fun distanceSqr(other: BlockPos3): Double = center().distanceSqr(other.center())

    fun distance(other: BlockPos3): Double = center().distance(other.center())

    /** The six blocks sharing a face with this one. */
    fun faceNeighbours(): List<BlockPos3> = listOf(
        copy(x = x - 1), copy(x = x + 1),
        copy(y = y - 1), copy(y = y + 1),
        copy(z = z - 1), copy(z = z + 1),
    )
}

/** World-space box (hitbox or block cube). */
data class Box3d(val min: Vec3d, val max: Vec3d) {
    fun center() = Vec3d((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2)

    /**
     * Scales the face around the center but keeps the thinnest side, so a button stays flat on its wall. On a
     * floor/ceiling [height] takes z and [width] x.
     */
    fun scaledFace(width: Double, height: Double): Box3d {
        val sizes = doubleArrayOf(max.x - min.x, max.y - min.y, max.z - min.z)
        val depthAxis = sizes.indices.minBy { sizes[it] }
        val heightAxis = if (depthAxis == 1) 2 else 1
        val c = center()
        val mids = doubleArrayOf(c.x, c.y, c.z)
        fun half(axis: Int) = sizes[axis] / 2 * when (axis) {
            depthAxis -> 1.0
            heightAxis -> height
            else -> width
        }
        fun lo(axis: Int) = mids[axis] - half(axis)
        fun hi(axis: Int) = mids[axis] + half(axis)
        return Box3d(Vec3d(lo(0), lo(1), lo(2)), Vec3d(hi(0), hi(1), hi(2)))
    }

    /** [spread] 0 = center, 1 = whole box. Any spread under 1 lands on the shape (unless flat jitter is added). */
    fun randomPoint(spread: Double, random: Random = Random.Default): Vec3d {
        fun axis(lo: Double, hi: Double) = (lo + hi) / 2 + (random.nextDouble() - 0.5) * (hi - lo) * spread
        return Vec3d(axis(min.x, max.x), axis(min.y, max.y), axis(min.z, max.z))
    }

    /**
     * Random point on the part visible from [eye]: facing sides are split into [grid] x [grid] cells over
     * their middle [spread], tried in random order until [visible] accepts one. Points sit inside the box so
     * rays always enter it. Null if nothing is visible. Up to `3 * grid * grid + 1` checks - fine per click,
     * not per frame.
     */
    fun visiblePoint(
        eye: Vec3d,
        spread: Double,
        grid: Int = 5,
        random: Random = Random.Default,
        visible: (Vec3d) -> Boolean,
    ): Vec3d? {
        val sx = spanOf(min.x, max.x, spread)
        val sy = spanOf(min.y, max.y, spread)
        val sz = spanOf(min.z, max.z, spread)
        val faces = buildList<(Double, Double) -> Vec3d> {
            facePlane(eye.x, min.x, max.x)?.let { x -> add { u, v -> Vec3d(x, sy.at(u), sz.at(v)) } }
            facePlane(eye.y, min.y, max.y)?.let { y -> add { u, v -> Vec3d(sx.at(u), y, sz.at(v)) } }
            facePlane(eye.z, min.z, max.z)?.let { z -> add { u, v -> Vec3d(sx.at(u), sy.at(v), z) } }
        }
        val cells = ArrayList<Triple<(Double, Double) -> Vec3d, Int, Int>>()
        for (face in faces) for (i in 0 until grid) for (j in 0 until grid) cells += Triple(face, i, j)
        val (face, i, j) = cells.shuffled(random).firstOrNull { (face, i, j) ->
            visible(face((i + 0.5) / grid, (j + 0.5) / grid))
        } ?: return null
        val spot = face((i + random.nextDouble()) / grid, (j + random.nextDouble()) / grid)
        return if (visible(spot)) spot else face((i + 0.5) / grid, (j + 0.5) / grid)
    }

    /** Faces included. */
    fun contains(x: Double, y: Double, z: Double): Boolean =
        x in min.x..max.x && y in min.y..max.y && z in min.z..max.z

    /** 0 inside. */
    fun distanceSqr(point: Vec3d): Double {
        val dx = maxOf(min.x - point.x, 0.0, point.x - max.x)
        val dy = maxOf(min.y - point.y, 0.0, point.y - max.y)
        val dz = maxOf(min.z - point.z, 0.0, point.z - max.z)
        return dx * dx + dy * dy + dz * dz
    }

    private fun Pair<Double, Double>.at(t: Double) = first + (second - first) * t

    companion object {
        /** Middle [spread] of `lo..hi`. */
        private fun spanOf(lo: Double, hi: Double, spread: Double): Pair<Double, Double> {
            val center = (lo + hi) / 2
            val half = (hi - lo) / 2 * spread
            return center - half to center + half
        }

        /** Null when [eye] is level with the box on this axis. */
        private fun facePlane(eye: Double, lo: Double, hi: Double): Double? =
            if (eye < lo || eye > hi) (lo + hi) / 2 else null

        fun of(block: BlockPos3) = Box3d(
            Vec3d(block.x.toDouble(), block.y.toDouble(), block.z.toDouble()),
            Vec3d(block.x + 1.0, block.y + 1.0, block.z + 1.0),
        )

        /** Corners in any order. */
        fun spanning(a: Vec3d, b: Vec3d) = Box3d(
            Vec3d(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z)),
            Vec3d(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z)),
        )
    }
}

/** Yaw 0 = south, clockwise; pitch -90 = up. Player yaw is unbounded: use [yawDelta] and [unwrap] before setting. */
object ViewAngles {
    /** Yaw wrapped to -180..180, pitch clamped. */
    fun between(eye: Vec3d, target: Vec3d): Pair<Float, Float> {
        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z
        val yaw = wrapYaw(Math.toDegrees(atan2(dz, dx)).toFloat() - 90f)
        val pitch = (-Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz)))).toFloat().coerceIn(-90f, 90f)
        return yaw to pitch
    }

    /** Unit vector; inverse of [between]. */
    fun direction(yaw: Float, pitch: Float): Vec3d {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        return Vec3d(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
    }

    /** -180..180. */
    fun wrapYaw(yaw: Float): Float = yaw.IEEErem(360f)

    /** Shortest signed turn. */
    fun yawDelta(from: Float, to: Float): Float = wrapYaw(to - from)

    /** Equivalent yaw closest to [near]. */
    fun unwrap(yaw: Float, near: Float): Float = near + yawDelta(near, yaw)
}
