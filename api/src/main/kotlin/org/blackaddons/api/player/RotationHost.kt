package org.blackaddons.api.player

import org.blackaddons.api.world.Box3d
import org.blackaddons.api.world.Vec3d
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Arbitrates the aim between features: [request] an [Aim], and the host decides whose is live.
 * Requests queue per arrival; [AimPriority.HIGH] takes over at once; an owner's newer request replaces
 * its old one. Aims are revoked when the owner disables and on world change.
 * Turns run per frame (Bezier or [BlackumAim]) and land on whole mouse steps ([turnStep]).
 * Yaw 0 = south, clockwise; pitch -90 = up. Any equivalent yaw works - the nearest one is taken.
 * Nothing turns while a screen is open.
 */
interface RotationHost {
    /** [silent] turns only the server rotation, leaving the camera alone. Thread-safe, as is every [Aim] command. */
    fun request(owner: Any, silent: Boolean = false, priority: AimPriority = AimPriority.NORMAL): Aim

    /** Smallest mouse turn at current sensitivity, in degrees; allow at least this for "aimed" checks. 0 if unknown. */
    val turnStep: Float get() = 0f
}

enum class AimPriority {
    NORMAL,

    /** Takes the aim from a NORMAL holder at once (e.g. Align). */
    HIGH,
}

/** One feature's aim lease. Commands given while waiting run once granted; on an ended aim they do nothing. */
interface Aim : Lease {
    val silent: Boolean

    /** Moving, holding a path node, handing back, or waiting with a turn queued. */
    val isTurning: Boolean

    /** For a path, false again once [advance] starts the next node. */
    val atTarget: Boolean

    fun turnTo(yaw: Float, pitch: Float, params: RotationParams = RotationParams())

    /** Re-aimed every frame as the player moves. */
    fun turnToPoint(target: Vec3d, params: RotationParams = RotationParams())

    /** One continuous spline through [path], stopping at each node; call [advance] once [atTarget]. */
    fun turnAlong(path: List<Vec3d>, params: RotationParams = RotationParams())

    fun advance()

    fun snap()

    /** Instantly, without a turn. */
    fun set(yaw: Float, pitch: Float)

    /** [handBack] decides what a silent aim leaves behind. */
    fun release(handBack: HandBack)

    override fun release() = release(HandBack.Discard)
}

/** What a released silent [Aim] leaves behind. */
sealed interface HandBack {
    /** Server rotation jumps back to the camera. */
    data object Discard : HandBack

    /** Camera jumps to the silent aim. */
    data object KeepView : HandBack

    /** Server rotation eases back at up to [degreesPerTick]. */
    data class Ease(val degreesPerTick: Float) : HandBack
}

/**
 * Per-call turn tuning; the host keeps no global rotation config.
 * @property speed rough degrees per tick.
 * @property humanize Bezier curve + jitter + noise; off = clean ease.
 * @property randomness jitter of the aim point in blocks. Ignores target size - prefer [Box3d.randomPoint].
 * @property curve Bezier bow strength (0 = straight).
 * @property smoothness 0..1 blend of [easing] over a constant rate.
 * @property noise per-frame micro-noise multiplier.
 * @property duration fixed real-time duration regardless of distance; [speed] is ignored.
 * @property blackum use the [BlackumAim] model instead of a Bezier.
 */
data class RotationParams(
    val speed: Float = 15f,
    val humanize: Boolean = true,
    val randomness: Float = 0f,
    val curve: Float = 0.15f,
    val easing: RotationEasing = RotationEasing.HUMAN,
    val smoothness: Float = 0.5f,
    val noise: Float = 1.0f,
    val duration: Duration? = null,
    val blackum: BlackumAim? = null,
)

/**
 * Blackum turn: reaction delay, one Fitts-timed flick with a noisy endpoint, up to three corrections, then
 * tracking of the player's own motion. Other [RotationParams] fields except `randomness` are ignored.
 * @property speed multiplier for the flick; reactions and corrections keep their pace.
 * @property reaction set by the caller, never inferred.
 * @property reactionTime median [ReactionMode.NORMAL] reaction.
 * @property targetRadius target half-width in blocks; drives flick time and miss correction. Unused for absolute turns.
 * @property hand per-player constants shared by every movement.
 */
data class BlackumAim(
    val speed: Float = 1f,
    val reaction: ReactionMode = ReactionMode.EXPECTED,
    val reactionTime: Duration = 150.milliseconds,
    val targetRadius: Float = 0.25f,
    val hand: AimHand = AimHand(),
)

enum class ReactionMode {
    /** Something new appeared. Takes about [BlackumAim.reactionTime]. */
    NORMAL,

    /** Position known beforehand: 10-30 ms. */
    EXPECTED,
}

/**
 * One simulated person, kept between turns so movements share a signature.
 * @property endpointNoise flick landing scatter per degree; lower = more skilled.
 * @property arc flick bow per degree.
 * @property visualDelay seconds the eye lags; own motion skips it.
 * @property selfMotionGain share of own motion followed at once, ~0.9-1.
 * @property selfMotionLag seconds to follow a change in own motion.
 * @property positionGain per second, pull on the error left while tracking.
 * @property refractory seconds between tracking corrections.
 * @property deadzone multiplier on the error a tracking correction waits for.
 * @property adaptTime seconds to learn what [selfMotionGain] misses.
 * @property reach mm the hand slides before lifting the mouse.
 * @property driftTime seconds for resting wander to decorrelate.
 * @property drift typical wander in degrees.
 * @property undershoot average share a flick falls short.
 */
data class AimHand(
    val tremorHz: Float = 8.75f,
    val tremorStrength: Float = 0.65f,
    val endpointNoise: Float = 0.24f,
    val rightHanded: Boolean = true,
    val arc: Float = 0.07f,
    val visualDelay: Float = 0.165f,
    val selfMotionGain: Float = 0.95f,
    val selfMotionLag: Float = 0.02f,
    val positionGain: Float = 3f,
    val refractory: Float = 0.3f,
    val deadzone: Float = 1f,
    val adaptTime: Float = 1f,
    val reach: Float = 150f,
    val driftTime: Float = 1.5f,
    val drift: Float = 0.1f,
    val undershoot: Float = 0.05f,
)
