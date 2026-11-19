package org.blackaddons.cheat.actions

import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.api.player.RotationParams
import org.blackaddons.cheat.aim.AimCurve
import org.blackaddons.core.features.impl.settings.Actions

/** ROTATE settings. They go into [Actions] but live here, so legit builds don't show dead controls. */
internal object RotateSettings : FeatureExtension(Actions) {

    private val rotationJitter by number("Rotation Jitter", default = 0.07, min = 0.0, max = 0.3, step = 0.01, unit = "blocks")
        .withDescription("Random offset added to the point a ROTATE step aims at, so repeated runs do not land on the exact same spot. Has no effect on Insta Snap steps or steps that aim by angle.")

    private val rotation = AimCurve(this)

    /** In blocks, for coordinate ROTATE steps. */
    val jitter: Float get() = rotationJitter.value.toFloat()

    fun params(speed: Float, humanize: Boolean): RotationParams = rotation.params(randomness = jitter, humanize = humanize, speed = speed)
}
