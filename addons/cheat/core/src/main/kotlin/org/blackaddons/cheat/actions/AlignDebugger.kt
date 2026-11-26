package org.blackaddons.cheat.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.util.dev.DevChat
import org.blackaddons.api.world.Vec3d
import org.blackaddons.api.world.WorldRenderer
import org.blackaddons.cheat.CheatAddon
import java.awt.Color
import java.util.Locale
import kotlin.math.hypot
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

object AlignDebugger {
    private val logger = CheatAddon.log.area("align")
    private val VISUAL_KEEP = 5.seconds

    var active: Boolean = false
        private set

    var targetX: Double = 0.0
        private set
    var targetZ: Double = 0.0
        private set
    var startX: Double = 0.0
        private set
    var startZ: Double = 0.0
        private set
    var playerY: Double = 0.0
        private set

    private var finishedAt: TimeSource.Monotonic.ValueTimeMark? = null
    var finalErrMm: Double = 0.0
        private set

    val predictedPoints = ArrayList<Vec3d>()
    val actualPoints = ArrayList<Vec3d>()

    fun onStart(tx: Double, tz: Double, px: Double, py: Double, pz: Double, vx: Double, vz: Double, isCrouching: Boolean) {
        targetX = tx
        targetZ = tz
        startX = px
        startZ = pz
        playerY = py
        active = true
        finalErrMm = 0.0
        finishedAt = null

        actualPoints.clear()
        predictedPoints.clear()

        actualPoints.add(Vec3d(px, py, pz))
        updateApproachingPredictedPath(px, py, pz, tx, tz)

        logger.debug(
            "ALIGN STARTED -> Target: ({}, {}) | Start: ({}, {}) | Dist: {}b | Vel: ({}, {}) [speed: {}b/t] | InitialCrouch: {}",
            String.format(Locale.ROOT, "%.4f", targetX), String.format(Locale.ROOT, "%.4f", targetZ),
            String.format(Locale.ROOT, "%.4f", startX), String.format(Locale.ROOT, "%.4f", startZ),
            String.format(Locale.ROOT, "%.4f", hypot(targetX - startX, targetZ - startZ)),
            String.format(Locale.ROOT, "%.5f", vx), String.format(Locale.ROOT, "%.5f", vz),
            String.format(Locale.ROOT, "%.5f", hypot(vx, vz)),
            isCrouching
        )
    }

    fun onTick(px: Double, py: Double, pz: Double) {
        if (!active) return
        actualPoints.add(Vec3d(px, py, pz))
    }

    fun updateApproachingPredictedPath(px: Double, py: Double, pz: Double, tx: Double, tz: Double) {
        predictedPoints.clear()
        predictedPoints.add(Vec3d(px, py, pz))
        predictedPoints.add(Vec3d(tx, py, tz))
    }

    fun onSnapSolved(
        path: List<Vec3d>,
        attempts: Int,
        maxAttempts: Int,
        yaw1: Float,
        sneak1: Boolean,
        yaw2: Float,
        sneak2: Boolean,
        errMm: Double
    ) {
        predictedPoints.clear()
        predictedPoints.addAll(path)

        logger.debug(
            "ALIGN SOLVED (attempt {}/{}) -> Yaws: yaw1={} deg (sneak={}), yaw2={} deg (sneak={}) | Mode Pred Err: {}mm",
            attempts, maxAttempts,
            String.format(Locale.ROOT, "%.2f", yaw1), sneak1,
            if (yaw2.isNaN()) "N/A" else String.format(Locale.ROOT, "%.2f", yaw2), sneak2,
            String.format(Locale.ROOT, "%.4f", errMm)
        )
    }

    fun onFailMaxAttempts(maxAttempts: Int) {
        val msg = "Align failed after $maxAttempts attempts"
        if (AlignSettings.isDebuggerEnabled) {
            Platform.chat.local(msg, Color(255, 85, 85))
        } else if (DevChat.enabled) {
            DevChat.log(msg)
        }
    }

    fun onFinish(tx: Double, tz: Double, px: Double, pz: Double, attempts: Int, maxAttempts: Int, totalTicks: Int, startedAt: TimeSource.Monotonic.ValueTimeMark) {
        val endDist = hypot(tx - px, tz - pz)
        finalErrMm = endDist * 1000.0
        val now = TimeSource.Monotonic.markNow()
        finishedAt = now
        val durationMs = (now - startedAt).inWholeMilliseconds

        val dxMm = (px - tx) * 1000.0
        val dzMm = (pz - tz) * 1000.0

        logger.debug(
            "ALIGN FINISHED -> Attempts: {}/{} | Total Ticks: {} ({} ms) | Target: ({}, {}) | Final Pos: ({}, {}) | Offset: dX={}mm, dZ={}mm (Total: {}mm)",
            attempts, maxAttempts, totalTicks, durationMs,
            String.format(Locale.ROOT, "%.6f", tx), String.format(Locale.ROOT, "%.6f", tz),
            String.format(Locale.ROOT, "%.6f", px), String.format(Locale.ROOT, "%.6f", pz),
            String.format(Locale.ROOT, "%+.4f", dxMm), String.format(Locale.ROOT, "%+.4f", dzMm),
            String.format(Locale.ROOT, "%.4f", finalErrMm)
        )

        active = false
    }

    fun onCancel(totalTicks: Int, attempts: Int) {
        active = false
        logger.debug("ALIGN CANCELLED after {} ticks (attempts: {})", totalTicks, attempts)
    }

    fun onRenderWorld() {
        if (!AlignSettings.isDebuggerEnabled) return

        val keepVisuals = active || finishedAt?.let { it.elapsedNow() < VISUAL_KEEP } == true
        if (!keepVisuals || targetX == 0.0) return

        val py = playerY
        val renderer = Platform.worldRenderer

        val targetColor = if (active) Color(0, 255, 120, 200) else Color(0, 200, 255, 180)
        renderer.ring(targetX, py + 0.02, targetZ, 0.25, targetColor, thickness = 3f)
        renderer.boxEdges(targetX - 0.1, py + 0.01, targetZ - 0.1, targetX + 0.1, py + 0.15, targetZ + 0.1, targetColor, thickness = 2f)

        val errLabel = if (active) "Aligning..." else String.format(Locale.ROOT, "%.2fmm", finalErrMm)
        renderer.text("OFFSET [$errLabel]", targetX, py + 0.25, targetZ, targetColor, scale = 0.9f)

        renderer.ring(startX, py + 0.02, startZ, 0.2, Color(255, 230, 0, 180), thickness = 2f)

        drawPath(renderer, predictedPoints, 0.04, Color(0, 255, 255, 220), 2.5f, Color(0, 255, 255, 255), 0.03)
        drawPath(renderer, actualPoints, 0.02, Color(255, 0, 180, 240), 3.0f, Color(255, 0, 180, 255), 0.02)
    }

    private fun drawPath(
        renderer: WorldRenderer,
        points: List<Vec3d>,
        yOffset: Double,
        lineColor: Color,
        thickness: Float,
        boxColor: Color,
        boxRadius: Double,
    ) {
        if (points.size < 2) return
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            renderer.line(p1.x, p1.y + yOffset, p1.z, p2.x, p2.y + yOffset, p2.z, lineColor, thickness)
            renderer.boxEdges(p2.x - boxRadius, p2.y + yOffset, p2.z - boxRadius, p2.x + boxRadius, p2.y + yOffset + 0.04, p2.z + boxRadius, boxColor)
        }
    }
}
