package org.blackaddons.core.control

import org.blackaddons.api.player.*
import org.blackaddons.api.util.TICKS_PER_SECOND
import org.blackaddons.api.world.Vec3d
import org.blackaddons.api.world.ViewAngles
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * [RotationHost] over a [ViewPort]. [onFrame], [onTick] and [onWorldChange] run on the client thread. [request] and
 * [Aim] commands can come from any thread and go through [ViewPort.execute]. A turn is a Bezier segment or a
 * [BlackumMotion], moved forward by the frame delta. Released silent aims come back here (discard, keep view or ease to
 * cursor).
 */
class AimControl(
    private val port: ViewPort,
    private val random: Random = Random(),
    private val clock: () -> Long = System::nanoTime,
) : RotationHost {

    private val arbiter = LeaseArbiter(object : LeaseArbiter.Events<AimLease> {
        override fun granted(lease: AimLease) = onGranted(lease)
        override fun ended(lease: AimLease) = onEnded(lease)
    })

    // A released silent aim that eases back to the cursor. The arbiter grants nothing meanwhile.
    private var easing: AimLease? = null
    private var easeSpeed = 0f

    // A silent aim ended. The slot clears when no other silent aim takes over.
    private var discardPending = false

    private var holding = false
    private var keepViewPending = false

    override val turnStep: Float get() = port.turnStep

    override fun request(owner: Any, silent: Boolean, priority: AimPriority): Aim {
        val lease = AimLease(owner, silent, priority)
        port.execute {
            val takesOver = easing?.let { it.owner === owner || priority.ordinal > it.priority } == true
            if (takesOver) {
                easing = null
                arbiter.held = false
                // If left set, the half-eased server rotation would freeze a camera aim.
                if (!silent) discardPending = true
            }
            arbiter.request(lease, first = takesOver)
            settle()
        }
        return lease
    }

    fun onFrame(deltaTicks: Float) {
        arbiter.active?.frame(deltaTicks)
    }

    /** Holds a released silent rotation until the next [onTick], so hand-back misses the click tick's movement packet. */
    fun holdThroughTick() {
        holding = true
    }

    /** Turns the live aim by [yawOffset] degrees at once. False if no aim is live or it's still turning. */
    fun nudge(yawOffset: Float): Boolean {
        val lease = arbiter.active ?: return false
        if (lease.isTurning || !port.inWorld || port.screenOpen) return false
        lease.nudge(yawOffset)
        return true
    }

    fun onTick() {
        holding = false
        if (keepViewPending) {
            keepViewPending = false
            port.silentToView()
        }
        arbiter.tick()
        easeBack()
        settle()
    }

    fun onWorldChange() {
        arbiter.clear()
        easing = null
        arbiter.held = false
        discardPending = false
        holding = false
        keepViewPending = false
        port.clearSilent()
    }

    private fun onGranted(lease: AimLease) {
        if (lease.silent && port.silentYaw == null && port.inWorld) port.setSilent(port.viewYaw, port.viewPitch)
        lease.replay()
    }

    private fun onEnded(lease: AimLease) {
        lease.stopMotion()
        if (!lease.silent) return
        when (val back = lease.handBack) {
            HandBack.Discard -> discardPending = true
            HandBack.KeepView -> if (holding) keepViewPending = true else port.silentToView()
            is HandBack.Ease -> {
                easing = lease
                easeSpeed = back.degreesPerTick
                arbiter.held = true
            }
        }
    }

    private fun settle() {
        if (!discardPending || holding) return
        discardPending = false
        val next = arbiter.active
        if (easing == null && (next == null || !next.silent)) port.clearSilent()
    }

    private fun easeBack() {
        if (easing == null) return
        val yaw = port.silentYaw
        val pitch = port.silentPitch
        if (yaw == null || pitch == null || !port.inWorld) {
            finishEasing()
            return
        }
        val yawDelta = ViewAngles.yawDelta(yaw, port.viewYaw)
        val pitchDelta = port.viewPitch - pitch
        val distance = hypot(yawDelta, pitchDelta)
        // A target closer than one mouse step is unreachable, so it counts as arrived.
        val step = port.turnStep
        if (distance <= maxOf(ARRIVED_DEGREES, step)) {
            port.clearSilent()
            finishEasing()
            return
        }
        val fraction = (maxOf(easeSpeed, step) / distance).coerceAtMost(1f)
        port.setSilent(yaw + yawDelta * fraction, pitch + pitchDelta * fraction)
    }

    private fun finishEasing() {
        easing = null
        arbiter.resume()
    }

    private inner class AimLease(
        owner: Any,
        override val silent: Boolean,
        priority: AimPriority,
    ) : LeaseArbiter.Entry(owner, priority.ordinal), Aim {

        var handBack: HandBack = HandBack.Discard
            private set

        // Queued while waiting. A new turn replaces them.
        private val queued = ArrayList<() -> Unit>()

        private var segment: BezierSegment? = null
        private var blackum: BlackumMotion? = null
        private var cursorX = 0.0
        private var cursorY = 0.0
        private var params = RotationParams()
        // Unwrapped relative to the segment's start yaw.
        private var goalYaw = 0f
        private var goalPitch = 0f
        // Recalculated every frame. Null for an absolute angle.
        private var targetPoint: Vec3d? = null
        private var path: List<Vec3d>? = null
        private var pathIndex = 0
        private var progress = 0f
        // Unused with a fixed duration.
        private var speedTicks = 1f
        private var lastFrameNanos = 0L
        private var moving = false
        private var reached = false

        override val isTurning: Boolean
            get() = when (state) {
                LeaseState.WAITING -> queued.isNotEmpty()
                LeaseState.ACTIVE -> moving
                else -> this === easing
            }

        override val atTarget: Boolean get() = isActive && reached

        override fun turnTo(yaw: Float, pitch: Float, params: RotationParams) = command(newTurn = true) {
            this.params = params
            begin(yaw, pitch.coerceIn(-90f, 90f), nextDir = null)
        }

        override fun turnToPoint(target: Vec3d, params: RotationParams) = command(newTurn = true) {
            this.params = params
            val point = jitter(target, params).also { targetPoint = it }
            val (yaw, pitch) = port.anglesTo(point)
            begin(yaw, pitch, nextDir = null)
        }

        override fun turnAlong(path: List<Vec3d>, params: RotationParams) = command(newTurn = true) {
            if (path.isEmpty()) return@command
            this.params = params
            this.path = path.map { jitter(it, params) }
            pathIndex = 0
            startNode()
        }

        override fun advance() = command {
            val nodes = path ?: run { stopMotion(); return@command }
            if (pathIndex < nodes.lastIndex) {
                pathIndex++
                startNode()
            } else {
                path = null
            }
        }

        override fun snap() = command {
            if (segment == null && blackum == null) return@command
            blackum = null
            progress = 1f
            reached = true
            apply(goalYaw, goalPitch.coerceIn(-90f, 90f))
            if (path == null) moving = false
        }

        override fun set(yaw: Float, pitch: Float) = command(newTurn = true) {
            reached = true
            apply(yaw, pitch.coerceIn(-90f, 90f))
        }

        override fun release(handBack: HandBack) = port.execute {
            if (isEnded) return@execute
            this.handBack = handBack
            queued.clear()
            arbiter.release(this)
            settle()
        }

        override fun release() = release(HandBack.Discard)

        fun nudge(yawOffset: Float) {
            stopMotion()
            reached = true
            apply(currentYaw() + yawOffset, currentPitch())
        }

        fun replay() {
            val commands = queued.toList()
            queued.clear()
            commands.forEach { it() }
        }

        fun stopMotion() {
            segment = null
            blackum = null
            moving = false
            path = null
            targetPoint = null
        }

        fun frame(deltaTicks: Float) {
            blackum?.let {
                frameBlackum(it, deltaTicks)
                return
            }
            val seg = segment ?: return
            if (!moving) return
            if (!port.inWorld || port.screenOpen) {
                moving = false
                return
            }

            retarget()

            progress = nextProgress(deltaTicks)
            val t = progress
            var (yaw, pitch) = seg.sample(t, goalYaw, goalPitch)

            if (params.humanize && params.noise > 0f && t < 1f) {
                val time = (clock() / 1_000_000L % 10_000L) / 1000.0
                val scale = (1f - t) * 0.5f * params.noise
                yaw += (sin(time * 15.0) * cos(time * 7.0)).toFloat() * scale
                pitch += (cos(time * 13.0) * sin(time * 11.0)).toFloat() * scale
                // Unbiased jitter, so the path isn't periodic.
                yaw += (random.nextFloat() - 0.5f) * scale
                pitch += (random.nextFloat() - 0.5f) * scale
            }

            apply(yaw, pitch.coerceIn(-90f, 90f))

            if (progress >= 1f) {
                reached = true
                // A path holds on its node until advance().
                if (path == null) moving = false
            }
        }

        // Keeps a tracked block centered while the player drifts. Unwrapped against the running goal.
        private fun retarget() {
            targetPoint?.let {
                val (rawYaw, pitch) = port.anglesTo(it)
                goalYaw = ViewAngles.unwrap(rawYaw, near = goalYaw)
                goalPitch = pitch
            }
        }

        /* Keeps running through the hold. Only a screen pauses it. */
        private fun frameBlackum(motion: BlackumMotion, deltaTicks: Float) {
            val dt = (deltaTicks.toDouble() / TICKS_PER_SECOND).coerceIn(0.0, MAX_FRAME_SECONDS)
            if (!port.inWorld || port.screenOpen) return
            retarget()
            val yaw = currentYaw()
            val pitch = currentPitch()
            if (!silent && mouseMoved()) motion.interrupt()
            val (dy, dp) = motion.frame(
                dt, yaw, pitch, goalYaw, goalPitch, targetPoint, port.eyePos, port.eyeVelocity, port.partialTick, port.turnStep,
            )
            if (dy != 0f || dp != 0f) apply(yaw + dy, (pitch + dp).coerceIn(-90f, 90f))
            reached = motion.holding
            moving = !reached
        }

        /* The physical mouse, not the camera, so other view changes don't look like the player taking over. */
        private fun mouseMoved(): Boolean {
            val x = port.cursorX
            val y = port.cursorY
            val moved = x != cursorX || y != cursorY
            cursorX = x
            cursorY = y
            return moved
        }

        private fun command(newTurn: Boolean = false, block: () -> Unit) = port.execute {
            when (state) {
                LeaseState.WAITING -> {
                    if (newTurn) queued.clear()
                    queued += block
                }
                LeaseState.ACTIVE -> if (port.inWorld && !port.screenOpen) {
                    if (newTurn) stopMotion()
                    block()
                }
                else -> Unit
            }
        }

        private fun startNode() {
            val nodes = path ?: return
            val point = nodes[pathIndex].also { targetPoint = it }
            val (yaw, pitch) = port.anglesTo(point)
            val next = nodes.getOrNull(pathIndex + 1)?.let(port::anglesTo)
            begin(yaw, pitch, next)
        }

        private fun begin(rawGoalYaw: Float, pitch: Float, nextDir: Pair<Float, Float>?) {
            val startYaw = currentYaw()
            val startPitch = currentPitch()
            goalYaw = ViewAngles.unwrap(rawGoalYaw, near = startYaw)
            goalPitch = pitch
            val blackumParams = params.blackum
            if (blackumParams != null) {
                blackum = BlackumMotion(blackumParams, random)
                cursorX = port.cursorX
                cursorY = port.cursorY
                segment = null
            } else {
                blackum = null
                val seg = BezierSegment(startYaw, startPitch, goalYaw, goalPitch, params, random, nextDir)
                segment = seg
                speedTicks = (seg.distance / params.speed.coerceAtLeast(0.1f)).coerceAtLeast(1f)
            }
            progress = 0f
            lastFrameNanos = clock()
            reached = false
            moving = true
        }

        // A fixed duration is real time. A speed follows game ticks.
        private fun nextProgress(deltaTicks: Float): Float {
            val now = clock()
            val step = params.duration
                ?.let { (now - lastFrameNanos).toFloat() / it.inWholeNanoseconds.coerceAtLeast(1L) }
                ?: (deltaTicks / speedTicks)
            lastFrameNanos = now
            return (progress + step).coerceAtMost(1f)
        }

        private fun currentYaw(): Float = if (silent) port.silentYaw ?: port.viewYaw else port.viewYaw

        private fun currentPitch(): Float = if (silent) port.silentPitch ?: port.viewPitch else port.viewPitch

        private fun apply(yaw: Float, pitch: Float) {
            if (silent) port.setSilent(yaw, pitch) else port.setView(yaw, pitch)
        }

        private fun jitter(target: Vec3d, params: RotationParams): Vec3d {
            if (!params.humanize || params.randomness <= 0f) return target
            val r = params.randomness
            return Vec3d(
                target.x + (random.nextFloat() * 2f - 1f) * r,
                target.y + (random.nextFloat() * 2f - 1f) * r,
                target.z + (random.nextFloat() * 2f - 1f) * r,
            )
        }
    }

    private companion object {
        const val ARRIVED_DEGREES = 0.01f
        const val MAX_FRAME_SECONDS = 0.1
    }
}
