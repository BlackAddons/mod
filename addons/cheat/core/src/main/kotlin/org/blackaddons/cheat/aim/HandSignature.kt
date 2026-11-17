package org.blackaddons.cheat.aim

import org.blackaddons.api.player.AimHand
import java.util.Random

/* One person's quirks: a z-score for each [HandTrait], drawn from [seed]. Skill comes from presets. */
internal class HandSignature(val seed: Int) {

    private val random = Random(seed.toLong())
    private val scores = DoubleArray(HandTrait.entries.size) { random.nextGaussian().coerceIn(-MAX_SCORE, MAX_SCORE) }

    /* About one person in ten is left-handed. */
    val rightHanded = random.nextDouble() >= LEFT_SHARE

    fun value(trait: HandTrait): Double = (trait.median + scores[trait.ordinal] * trait.spread).coerceIn(trait.min, trait.max)

    private companion object {
        const val MAX_SCORE = 2.5
        const val LEFT_SHARE = 0.1
    }
}

/* Scores are drawn by ordinal, so only append new traits and never reorder them, or every saved hand changes. */
internal enum class HandTrait(val median: Double, val spread: Double, val min: Double, val max: Double) {
    TREMOR_HZ(8.75, 0.6, 7.5, 10.0),
    ARC(0.07, 0.021, 0.04, 0.10),
    VISUAL_DELAY(0.165, 0.015, 0.130, 0.200),
    SELF_MOTION_LAG(0.02, 0.01, 0.005, 0.045),
    REACH(150.0, 30.0, 100.0, 200.0),
    DRIFT_TIME(1.5, 0.4, 0.8, 2.5),
    SELF_MOTION_GAIN(0.0, 0.02, -0.05, 0.05),
}

internal fun aimHand(preset: AimPreset, signature: HandSignature): AimHand {
    fun trait(trait: HandTrait) = signature.value(trait).toFloat()
    return AimHand(
        tremorHz = trait(HandTrait.TREMOR_HZ),
        tremorStrength = preset.tremorStrength,
        endpointNoise = preset.endpointNoise,
        rightHanded = signature.rightHanded,
        arc = trait(HandTrait.ARC) * preset.arcScale,
        visualDelay = trait(HandTrait.VISUAL_DELAY),
        selfMotionGain = (preset.selfMotionGain + trait(HandTrait.SELF_MOTION_GAIN)).coerceAtMost(1f),
        selfMotionLag = trait(HandTrait.SELF_MOTION_LAG),
        positionGain = preset.positionGain,
        refractory = preset.refractory,
        deadzone = preset.deadzone,
        adaptTime = preset.adaptTime,
        reach = trait(HandTrait.REACH),
        driftTime = trait(HandTrait.DRIFT_TIME),
        drift = preset.drift,
        undershoot = preset.undershoot,
    )
}
