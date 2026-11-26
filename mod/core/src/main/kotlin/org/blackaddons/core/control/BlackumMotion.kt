package org.blackaddons.core.control

import org.blackaddons.api.player.BlackumAim
import org.blackaddons.api.player.ReactionMode
import org.blackaddons.api.util.TICKS_PER_SECOND
import org.blackaddons.api.util.expSmoothing
import org.blackaddons.api.world.Vec3d
import org.blackaddons.api.world.ViewAngles
import org.blackaddons.core.control.CorrectionTable.CORRECTION_GAIN
import org.blackaddons.core.control.CorrectionTable.MAX_CORRECTIONS
import java.util.Random
import kotlin.math.*
import kotlin.time.DurationUnit

/*
 * One Blackum target acquisition (see "Blackum Rotation Algorithm - Spec"): REACT, a Fitts-timed flick, up to three GAP
 * + CORRECT submovements, then TRACK. Motion plus [Tremor] snaps to mouse counts, so tremor below one count disappears,
 * like on a real sensor. DPI isn't modeled, the turn step carries sensitivity. Own motion is known from the motor
 * command, not seen late, so it drives the hand right away and the delayed view only corrects the rest: a strafe
 * reversal costs no visual delay. The source is public to read, but reusing it outside BlackAddons needs the team's
 * permission: see PERMISSIONS.md.
 */
internal class BlackumMotion(private val params: BlackumAim, private val random: Random) {

    private enum class Stage { REACT, PRIMARY, GAP, CORRECT, TRACK }

    private var stage = Stage.REACT

    val holding: Boolean get() = stage == Stage.TRACK

    private val hand = params.hand
    private val speed = params.speed.toDouble().coerceAtLeast(MIN_SPEED)
    private val arc = (if (hand.rightHanded) 1.0 else -1.0) * hand.arc
    private val primaryGain = 1.0 - hand.undershoot
    private val reach = hand.reach / MM_PER_INCH * NOMINAL_CPI

    private val reactionSpread = (params.reactionTime.toDouble(DurationUnit.SECONDS) - RT_FLOOR).coerceAtLeast(MIN_RT_SPREAD)

    private var now = 0.0
    private var stageTime = 0.0
    val reactTime = when (params.reaction) {
        ReactionMode.NORMAL -> RT_FLOOR + exp(ln(reactionSpread) + 0.25 * gauss())
        ReactionMode.EXPECTED -> uniform(0.010, 0.030)
    }
    private var gapTime = 0.0
    private var corrections = 0
    private var sinceCorrection = 0.0
    private var refractory = refractoryDraw()
    private var move: Submovement? = null

    /* The player took the mouse, so no pulling against them until the next plan. */
    private var yielded = false

    private val history = PursuitBuffer()
    private var lastShownYaw = Double.NaN
    private var lastShownPitch = 0.0

    private var adapt = 0.0
    private val gainWander = Wander(random)

    private var feedYaw = 0.0
    private var feedPitch = 0.0

    private var lastAimYaw = Float.NaN
    private var lastAimPitch = 0f
    private var aimRateYaw = 0.0
    private var aimRatePitch = 0.0

    private val tremorYaw = Tremor(random)
    private val tremorPitch = Tremor(random)
    private val driftYaw = Wander(random)
    private val driftPitch = Wander(random)
    private var countsYaw = 0.0
    private var countsPitch = 0.0

    /* Whole mouse steps, or degrees while the step is unknown. The hand sees the eye interpolated by [partialTick]. */
    fun frame(
        dt: Double,
        aimYaw: Float,
        aimPitch: Float,
        goalYaw: Float,
        goalPitch: Float,
        point: Vec3d?,
        eye: Vec3d,
        eyeVelocity: Vec3d,
        partialTick: Float,
        step: Float,
    ): Pair<Float, Float> {
        now += dt
        stageTime += dt
        sinceCorrection += dt
        trackAimRate(dt, aimYaw, aimPitch)
        val width = point?.let { angularWidth(it, eye) } ?: max(ABSOLUTE_WIDTH, 4.0 * step)

        val (selfYaw, selfPitch) = point?.let { selfMotion(it, eye, eyeVelocity) } ?: (0.0 to 0.0)
        val selfSpeed = hypot(selfYaw, selfPitch)
        val behind = (1.0 - partialTick.coerceIn(0f, 1f)) / TICKS_PER_SECOND
        val shownYaw = goalYaw - selfYaw * behind
        val shownPitch = goalPitch - selfPitch * behind
        history.push(now, shownYaw, shownPitch)

        /* The rate on screen, so a frame that spans a tick moves by what was rendered. */
        val gain = selfGain(dt)
        val firstFrame = lastShownYaw.isNaN() || dt <= 0
        val shownRateYaw = if (firstFrame) selfYaw else (shownYaw - lastShownYaw) / dt
        val shownRatePitch = if (firstFrame) selfPitch else (shownPitch - lastShownPitch) / dt
        lastShownYaw = shownYaw
        lastShownPitch = shownPitch
        val pursuing = stage != Stage.REACT
        follow(dt, if (pursuing) gain else 0.0, shownRateYaw, shownRatePitch)

        /*
         * What the eye saw one visual delay ago, carried forward by own motion since then. The target never moves
         * within a turn.
         */
        val seen = history.at(now - hand.visualDelay)
        val seenYaw = history.yaw[seen] + gain * (shownYaw - history.yaw[seen])
        val seenPitch = history.pitch[seen] + gain * (shownPitch - history.pitch[seen])
        val errYaw = ViewAngles.yawDelta(aimYaw, seenYaw.toFloat()) - feedYaw * dt
        val errPitch = seenPitch - aimPitch - feedPitch * dt
        val cosPitch = cos(Math.toRadians(aimPitch.toDouble()))
        val err = hypot(errYaw * cosPitch, errPitch)

        val moving = min(1.0, selfSpeed / PULL_SPEED)
        val deadzone = deadzone(width, step, selfSpeed, moving)
        var moveYaw = 0.0
        var movePitch = 0.0
        when (stage) {
            Stage.REACT -> if (stageTime >= reactTime) {
                if (err > width / 2) planPrimary(errYaw, errPitch, width, step) else enterTrack()
            }
            Stage.PRIMARY, Stage.CORRECT -> {
                val current = move ?: return 0f to 0f
                val (dy, dp) = current.advance(stageTime)
                moveYaw = dy
                movePitch = dp
                if (stageTime >= current.total) {
                    if (err > width / 2 && corrections < MAX_CORRECTIONS) enterGap() else enterTrack()
                }
            }
            Stage.GAP -> if (stageTime >= gapTime) {
                when {
                    err > lostAt(width) -> planPrimary(errYaw, errPitch, width, step)
                    err > deadzone / 2 -> planCorrection(errYaw, errPitch, err, width, step)
                    else -> enterTrack()
                }
            }
            Stage.TRACK -> {
                val closing = closingRate(gain * selfYaw, gain * selfPitch, errYaw, errPitch, err, cosPitch)
                val returning = closing < 0 && err / -closing < RETURN_WINDOW
                if (err > deadzone && sinceCorrection > refractory && !returning) enterGap()
            }
        }

        /*
         * Pull on the error the planned move won't remove. The flick is ballistic, and when far off, a new flick beats
         * a slow creep.
         */
        var pullYaw = 0.0
        var pullPitch = 0.0
        if (moving > 0 && !yielded && stage != Stage.REACT && stage != Stage.PRIMARY) {
            val (leftYaw, leftPitch) = move?.remaining() ?: (0.0 to 0.0)
            val restYaw = errYaw - leftYaw
            val restPitch = errPitch - leftPitch
            val rest = hypot(restYaw * cosPitch, restPitch)
            if (rest > deadzone / 2 && rest < lostAt(width)) {
                val k = hand.positionGain * moving * (1 - deadzone / 2 / rest)
                pullYaw = k * restYaw
                pullPitch = k * restPitch
                if (selfSpeed > ADAPT_SPEED) adaptTo(dt, pullYaw, pullPitch, selfYaw, selfPitch, selfSpeed)
            }
        }

        val driftTime = hand.driftTime.toDouble()
        val drift = hand.drift.toDouble()
        val intendedYaw = moveYaw + (if (pursuing) (feedYaw + pullYaw) * dt else 0.0) + driftYaw.step(dt, driftTime, drift)
        val intendedPitch = movePitch + (if (pursuing) (feedPitch + pullPitch) * dt else 0.0) + driftPitch.step(dt, driftTime, drift)
        return quantize(intendedYaw, intendedPitch, dt, step, tremorScale(pursuing))
    }

    /* The player moved the view: wait, then correct from where they stopped. Runs on every frame with movement. */
    fun interrupt() {
        if (stage == Stage.REACT) return
        if (!yielded) {
            countsYaw = 0.0
            countsPitch = 0.0
        }
        yielded = true
        corrections = 0
        enterGap()
    }

    private fun planPrimary(errYaw: Double, errPitch: Double, width: Double, step: Float) {
        val distance = hypot(errYaw, errPitch)
        val ratio = distance / width
        val difficulty = log2(ratio + 1)
        val fitts = (0.035 + 0.243 * difficulty) * (1 + 0.10 * abs(errPitch) / distance)
        val correctionBudget = CorrectionTable.expected(ratio, hand.endpointNoise.toDouble(), primaryGain) * (TC_MEAN + TGAP_MEAN)
        val duration = max(MIN_PRIMARY, fitts - correctionBudget) * exp(0.10 * gauss()) / speed
        plan(errYaw, errPitch, duration, primaryGain, (0.55 - 0.05 * difficulty).coerceIn(0.20, 0.55), step)
        corrections = 0
        sinceCorrection = 0.0
        stage = Stage.PRIMARY
    }

    /* Aim Speed only scales the flick. A faster correction looks like a snap. */
    private fun planCorrection(errYaw: Double, errPitch: Double, err: Double, width: Double, step: Float) {
        val fitts = (0.035 + 0.243 * log2(err / width + 1)).coerceIn(MIN_CORRECTION, MAX_CORRECTION)
        plan(errYaw, errPitch, fitts * exp(0.15 * gauss()), CORRECTION_GAIN, 0.5, step)
        corrections++
        sinceCorrection = 0.0
        refractory = refractoryDraw()
        stage = Stage.CORRECT
    }

    /* The feedforward carries own motion in the meantime, so the plan only covers the error. */
    private fun plan(errYaw: Double, errPitch: Double, duration: Double, along: Double, skew: Double, step: Float) {
        move = Submovement(
            yaw = errYaw,
            pitch = errPitch,
            duration = duration,
            along = along,
            skew = skew,
            endpointNoise = hand.endpointNoise.toDouble(),
            arc = arc,
            step = step,
            reachCounts = reach,
            random = random,
        )
        stageTime = 0.0
        yielded = false
    }

    private fun enterGap() {
        move = null
        gapTime = uniform(0.02, 0.06)
        stage = Stage.GAP
        stageTime = 0.0
    }

    private fun enterTrack() {
        move = null
        yielded = false
        corrections = 0
        stage = Stage.TRACK
        stageTime = 0.0
    }

    private fun deadzone(width: Double, step: Float, selfSpeed: Double, moving: Double): Double {
        val still = STILL_DEADZONE * width
        val tracking = (TRACK_DEADZONE * width).coerceIn(MIN_DEADZONE, MAX_DEADZONE) * hand.deadzone *
            (1 + DEADZONE_WIDENING * min(1.0, selfSpeed / WIDE_DEADZONE_SPEED))
        return max(step.toDouble(), still + (tracking - still) * moving)
    }

    private fun lostAt(width: Double): Double = max(LOST_WIDTHS * width, LOST_DEGREES)

    /*
     * How fast the error grows per second, negative means it shrinks. A person waits for an error that goes away on its
     * own.
     */
    private fun closingRate(targetYaw: Double, targetPitch: Double, errYaw: Double, errPitch: Double, err: Double, cosPitch: Double): Double {
        val rateYaw = targetYaw - aimRateYaw
        val ratePitch = targetPitch - aimRatePitch
        return (rateYaw * errYaw * cosPitch * cosPitch + ratePitch * errPitch) / err
    }

    private fun selfGain(dt: Double): Double {
        gainWander.step(dt, WANDER_TIME, WANDER)
        return (hand.selfMotionGain + adapt) * (1 + gainWander.value)
    }

    private fun follow(dt: Double, gain: Double, rateYaw: Double, ratePitch: Double) {
        var targetYaw = gain * rateYaw
        var targetPitch = gain * ratePitch
        val cap = MAX_PURSUIT * speed
        val size = hypot(targetYaw, targetPitch)
        if (size > cap) {
            targetYaw *= cap / size
            targetPitch *= cap / size
        }
        val blend = smoothing(dt, hand.selfMotionLag.toDouble())
        feedYaw += (targetYaw - feedYaw) * blend
        feedPitch += (targetPitch - feedPitch) * blend
    }

    /* A pull that keeps pointing along own motion means the feedforward has too little gain. */
    private fun adaptTo(dt: Double, pullYaw: Double, pullPitch: Double, selfYaw: Double, selfPitch: Double, selfSpeed: Double) {
        val share = (pullYaw * selfYaw + pullPitch * selfPitch) / (selfSpeed * selfSpeed)
        val time = hand.adaptTime.toDouble().coerceAtLeast(MIN_ADAPT_TIME)
        adapt = (adapt + share * dt / time).coerceIn(MIN_GAIN - hand.selfMotionGain, MAX_GAIN - hand.selfMotionGain)
    }

    private fun trackAimRate(dt: Double, aimYaw: Float, aimPitch: Float) {
        if (!lastAimYaw.isNaN() && dt > 0) {
            val blend = smoothing(dt, AIM_RATE_TIME)
            aimRateYaw += (ViewAngles.yawDelta(lastAimYaw, aimYaw) / dt - aimRateYaw) * blend
            aimRatePitch += ((aimPitch - lastAimPitch) / dt - aimRatePitch) * blend
        }
        lastAimYaw = aimYaw
        lastAimPitch = aimPitch
    }

    /* A hand that slides to follow passes on some tremor, a resting hand doesn't. */
    private fun tremorScale(pursuing: Boolean): Double = when {
        move != null -> 1.0
        pursuing -> PURSUIT_TREMOR * min(1.0, hypot(feedYaw, feedPitch) / SLIDE_SPEED)
        else -> 0.0
    }

    private fun quantize(yaw: Double, pitch: Double, dt: Double, step: Float, tremor: Double): Pair<Float, Float> {
        if (step <= 0f) return yaw.toFloat() to pitch.toFloat()
        val rms = TREMOR_METRES * hand.tremorStrength * NOMINAL_CPI / METRES_PER_INCH
        val shakeYaw = tremorYaw.step(dt, hand.tremorHz.toDouble(), rms)
        val shakePitch = tremorPitch.step(dt, hand.tremorHz.toDouble(), rms)
        if (move?.lifted == true) return 0f to 0f
        countsYaw += yaw / step + tremor * shakeYaw
        countsPitch += pitch / step + tremor * shakePitch
        val whole = countsYaw.toLong() to countsPitch.toLong()
        countsYaw -= whole.first
        countsPitch -= whole.second
        return whole.first * step to whole.second * step
    }

    private fun angularWidth(point: Vec3d, eye: Vec3d): Double =
        2 * Math.toDegrees(atan(params.targetRadius / point.distance(eye).coerceAtLeast(1e-3)))

    /* Analytic: the eye moves once per tick, so differences per frame would be steps. */
    private fun selfMotion(point: Vec3d, eye: Vec3d, eyeVelocity: Vec3d): Pair<Double, Double> {
        val rx = point.x - eye.x
        val ry = point.y - eye.y
        val rz = point.z - eye.z
        val vx = -eyeVelocity.x
        val vy = -eyeVelocity.y
        val vz = -eyeVelocity.z
        val flat = (rx * rx + rz * rz).coerceAtLeast(1e-9)
        val h = sqrt(flat)
        val yaw = (rx * vz - rz * vx) / flat * DEG
        val hDot = (rx * vx + rz * vz) / h
        val pitch = -(h * vy - ry * hDot) / (flat + ry * ry) * DEG
        return yaw to pitch
    }

    private fun refractoryDraw(): Double = hand.refractory * uniform(0.85, 1.15)

    private fun smoothing(dt: Double, time: Double): Double = if (time <= 0) 1.0 else expSmoothing(dt, 1 / time)

    private fun gauss(): Double = random.nextGaussian()

    private fun uniform(lo: Double, hi: Double): Double = lo + random.nextDouble() * (hi - lo)

    private companion object {
        const val MIN_SPEED = 0.05
        const val RT_FLOOR = 0.08
        const val MIN_RT_SPREAD = 0.01
        const val MIN_PRIMARY = 0.12
        const val MIN_CORRECTION = 0.12
        const val MAX_CORRECTION = 0.25
        const val TC_MEAN = 0.2023
        const val TGAP_MEAN = 0.04
        const val ABSOLUTE_WIDTH = 0.5
        const val MAX_PURSUIT = 90.0

        /* The old hold threshold while still, and Wolpert et al.'s 0.06-0.38 deg while moving. */
        const val STILL_DEADZONE = 0.2
        const val TRACK_DEADZONE = 0.15
        const val MIN_DEADZONE = 0.1
        const val MAX_DEADZONE = 0.4
        const val DEADZONE_WIDENING = 0.5
        const val WIDE_DEADZONE_SPEED = 60.0

        const val PULL_SPEED = 10.0
        const val ADAPT_SPEED = 5.0
        const val MIN_ADAPT_TIME = 0.05
        const val MIN_GAIN = 0.8
        const val MAX_GAIN = 1.03

        /* Farther off than this, a correction is a new flick. */
        const val LOST_WIDTHS = 2.0
        const val LOST_DEGREES = 3.0
        const val RETURN_WINDOW = 0.18
        const val AIM_RATE_TIME = 0.05

        const val WANDER = 0.03
        const val WANDER_TIME = 0.7

        const val PURSUIT_TREMOR = 0.5
        const val SLIDE_SPEED = 30.0
        const val TREMOR_METRES = 30e-6
        const val NOMINAL_CPI = 800.0
        const val METRES_PER_INCH = 0.0254
        const val MM_PER_INCH = 25.4
        const val DEG = 180.0 / PI
    }
}
