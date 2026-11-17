package org.blackaddons.cheat.aim

import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.formatPercent
import org.blackaddons.api.util.lerp
import org.blackaddons.api.util.logLerp
import kotlin.math.roundToInt

/* A skill level: each slider from 0 to 1 drives the hand values that change with skill. */
internal data class AimPreset(
    val name: String,
    val speed: Float,
    val reaction: Float,
    val precision: Float,
    val tracking: Float,
    val steadiness: Float,
) {
    val endpointNoise: Float get() = logLerp(0.30f, 0.04f, precision)
    val undershoot: Float get() = UNDERSHOOT_PER_SCATTER * endpointNoise
    val arcScale: Float get() = lerp(0.95f, 0.25f, precision)
    val tremorStrength: Float get() = logLerp(1.2f, 0.15f, steadiness)
    val drift: Float get() = DRIFT_PER_TREMOR * tremorStrength
    val selfMotionGain: Float get() = lerp(0.90f, 0.99f, tracking)
    val positionGain: Float get() = lerp(2f, 4f, tracking)
    val refractory: Float get() = lerp(0.35f, 0.25f, tracking)
    val deadzone: Float get() = lerp(1.3f, 0.7f, tracking)
    val adaptTime: Float get() = lerp(2f, 0.6f, tracking)

    /* `key=value;...;name=<name>`. Name goes last, so it can contain anything. */
    fun toLine(): String =
        PresetField.entries.joinToString(";", postfix = ";") { "${it.key}=${it.get(this)}" } + NAME_PREFIX + name

    companion object {
        private const val NAME_PREFIX = "name="
        private const val UNDERSHOOT_PER_SCATTER = 0.2f
        private const val DRIFT_PER_TREMOR = 0.135f

        val BUILT_IN = listOf(
            AimPreset("Cata 24", speed = 1.40f, reaction = 190f, precision = 0.31f, tracking = 0.35f, steadiness = 0.33f),
            AimPreset("Cata 30", speed = 1.75f, reaction = 170f, precision = 0.45f, tracking = 0.50f, steadiness = 0.47f),
            AimPreset("Cata 45", speed = 2.30f, reaction = 150f, precision = 0.66f, tracking = 0.70f, steadiness = 0.67f),
            AimPreset("Cata 50", speed = 3.50f, reaction = 135f, precision = 0.80f, tracking = 0.85f, steadiness = 0.86f),
        )

        val DEFAULT = BUILT_IN[2]

        /* Missing or broken values use the default's, so hand-edited configs still load. */
        fun fromLine(line: String): AimPreset? {
            val at = line.indexOf(";$NAME_PREFIX").let { if (it >= 0) it + 1 else if (line.startsWith(NAME_PREFIX)) 0 else return null }
            val name = line.substring(at + NAME_PREFIX.length).trim().takeIf { it.isNotEmpty() } ?: return null
            val values = line.substring(0, at).split(';').mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) null else pair.substring(0, eq) to pair.substring(eq + 1).toFloatOrNull()
            }.toMap()
            return PresetField.entries.fold(DEFAULT.copy(name = name)) { preset, field ->
                val value = values[field.key]
                if (value == null || value.isNaN()) preset else field.set(preset, value.coerceIn(field.min, field.max))
            }
        }
    }
}

/* Editor order. [key] is the saved name: never change it. */
internal enum class PresetField(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float,
    val get: (AimPreset) -> Float,
    val set: (AimPreset, Float) -> AimPreset,
    val display: (Float) -> String,
) {
    SPEED("speed", "Aim Speed", 0.5f, 6f, 0.05f, { it.speed }, { p, v -> p.copy(speed = v) }, { it.formatDecimals() + "x" }),
    REACTION("reaction", "Reaction", 100f, 300f, 5f, { it.reaction }, { p, v -> p.copy(reaction = v) }, { "${it.roundToInt()} ms" }),
    PRECISION("precision", "Precision", 0f, 1f, 0.01f, { it.precision }, { p, v -> p.copy(precision = v) }, Float::formatPercent),
    TRACKING("tracking", "Tracking", 0f, 1f, 0.01f, { it.tracking }, { p, v -> p.copy(tracking = v) }, Float::formatPercent),
    STEADINESS("steadiness", "Steadiness", 0f, 1f, 0.01f, { it.steadiness }, { p, v -> p.copy(steadiness = v) }, Float::formatPercent),
}
