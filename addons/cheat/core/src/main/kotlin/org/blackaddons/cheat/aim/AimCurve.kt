package org.blackaddons.cheat.aim

import org.blackaddons.api.feature.settings.SettingProvider
import org.blackaddons.api.feature.settings.impl.ModeSetting
import org.blackaddons.api.player.ReactionMode
import org.blackaddons.api.player.RotationParams
import org.blackaddons.cheat.features.impl.settings.Rotations

internal class AimCurve(provider: SettingProvider, groupLabel: String? = null, shown: () -> Boolean = { true }) {

    private val picker = Picker()

    init {
        with(provider) {
            picker.withDescription("The skill level the Blackum Curve aims at. Edit presets in Settings > Rotations.")
                .showIf(shown)
            if (groupLabel != null) picker.group(groupLabel)
            registerSetting(picker)
        }
        Rotations.track(picker)
    }

    /* A deleted or unloaded preset falls back to the default. */
    private val preset: AimPreset get() = Rotations.preset(picker.value) ?: AimPreset.DEFAULT

    fun params(
        reaction: ReactionMode = ReactionMode.EXPECTED,
        targetRadius: Float = DEFAULT_RADIUS,
        randomness: Float = 0f,
        humanize: Boolean = true,
        speed: Float = 1f, // Multiplies the preset's Aim Speed.
    ): RotationParams = RotationParams(randomness = randomness, humanize = humanize, blackum = Rotations.aim(preset, reaction, targetRadius, speed))

    class Picker : ModeSetting(NAME, AimPreset.DEFAULT.name, Options)

    /* [contains] accepts any name, so a saved choice survives even when it loads before the presets. */
    private object Options : AbstractList<String>() {
        private val names: List<String> get() = Rotations.presets.map { it.name }
        override val size: Int get() = names.size
        override fun get(index: Int): String = names[index]
        override fun contains(element: String): Boolean = true
    }

    companion object {
        const val DEFAULT_RADIUS = 0.15f
        private const val NAME = "Aim Preset"
    }
}
