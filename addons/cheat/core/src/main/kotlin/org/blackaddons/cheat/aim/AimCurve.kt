package org.blackaddons.cheat.aim

import org.blackaddons.api.feature.settings.SettingProvider
import org.blackaddons.api.feature.settings.impl.ModeSetting
import org.blackaddons.api.player.ReactionMode
import org.blackaddons.api.player.RotationEasing
import org.blackaddons.api.player.RotationParams
import org.blackaddons.cheat.features.impl.settings.Rotations

internal class AimCurve(provider: SettingProvider, groupLabel: String? = null, shown: () -> Boolean = { true }) {

    private val curve: ModeSetting
    private val picker = Picker()

    init {
        with(provider) {
            curve = mode("Rotation Curve", default = BLACKUM, modes = listOf(BLACKUM, HUMAN_OLD, EASE_OUT, LINEAR))
                .withDescription("Shape of the turn. The Blackum Curve is the Blackum Algorithm: a reaction, one flick with corrections, then a steady hold. A constant rate (Linear) is the one a hand never makes.")
                .unsafeWhen("UNSAFE", "A hand accelerates and settles; a constant-rate turn is the single most obvious tell there is") { it == LINEAR }
                .showIf(shown)
            picker.withDescription("The skill level the Blackum Curve aims at. Edit presets in Settings > Rotations.")
                .showIf { shown() && isBlackum }
            if (groupLabel != null) {
                curve.group(groupLabel)
                picker.group(groupLabel)
            }
            registerSetting(curve)
            registerSetting(picker)
        }
        Rotations.track(picker)
    }

    val isBlackum: Boolean get() = curve.value == BLACKUM

    /* A deleted or unloaded preset falls back to the default. */
    private val preset: AimPreset? get() = if (isBlackum) Rotations.preset(picker.value) ?: AimPreset.DEFAULT else null

    fun params(
        speed: Float,
        reaction: ReactionMode = ReactionMode.EXPECTED,
        targetRadius: Float = DEFAULT_RADIUS,
        randomness: Float = 0f,
        humanize: Boolean = true,
    ): RotationParams {
        val preset = preset ?: return RotationParams(
            speed = speed,
            humanize = humanize,
            randomness = randomness,
            easing = when (curve.value) {
                EASE_OUT -> RotationEasing.EASE_OUT_QUAD
                LINEAR -> RotationEasing.LINEAR
                else -> RotationEasing.HUMAN
            },
        )
        return RotationParams(randomness = randomness, humanize = humanize, blackum = Rotations.aim(preset, reaction, targetRadius))
    }

    class Picker : ModeSetting(NAME, AimPreset.DEFAULT.name, Options)

    /* [contains] accepts any name, so a saved choice survives even when it loads before the presets. */
    private object Options : AbstractList<String>() {
        private val names: List<String> get() = Rotations.presets.map { it.name }
        override val size: Int get() = names.size
        override fun get(index: Int): String = names[index]
        override fun contains(element: String): Boolean = true
    }

    companion object {
        const val BLACKUM = "Blackum Curve"
        const val HUMAN_OLD = "Human (Old)"
        const val EASE_OUT = "Ease Out"
        const val LINEAR = "Linear"
        const val DEFAULT_RADIUS = 0.15f
        private const val NAME = "Aim Preset"
    }
}
