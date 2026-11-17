package org.blackaddons.api.ui.theme

import java.awt.Color
import kotlin.math.abs
import kotlin.math.pow

/** Every other slot is derived from these. */
data class ThemeBases(val surface: Color, val text: Color, val accent: Color) {

    operator fun get(base: ThemeBase): Color = when (base) {
        ThemeBase.SURFACE -> surface
        ThemeBase.TEXT -> text
        ThemeBase.ACCENT -> accent
    }

    fun with(base: ThemeBase, color: Color): ThemeBases = when (base) {
        ThemeBase.SURFACE -> copy(surface = color)
        ThemeBase.TEXT -> copy(text = color)
        ThemeBase.ACCENT -> copy(accent = color)
    }
}

/**
 * A stored theme: [bases] plus hand-pinned slots. Deriving keeps hand-written themes coherent; overrides are
 * for cases a formula can't foresee (an accent colliding with SUCCESS or DANGER).
 */
data class ThemeSpec(val bases: ThemeBases, val overrides: Map<ThemeSlot, Color> = emptyMap()) {

    fun resolve(): Palette {
        val derived = derive(bases)
        return if (overrides.isEmpty()) derived else Palette { overrides[it] ?: derived[it] }
    }

    companion object {
        val MIDNIGHT = ThemeSpec(
            ThemeBases(
                surface = Color(28, 30, 38),
                text = Color(230, 237, 248),
                accent = Color(180, 109, 242),
            )
        )
    }
}

// Fitted to the original palette (within 2/255 on every surface).
private const val SURFACE_STEP = 0.070f
private const val SURFACE_FADE = 0.925f

// Per text tier: share of distance to the surface kept, and how far saturation moves toward it.
private const val TEXT_KEEP = 0.69f
private const val TEXT_TINT = 0.245f

private const val BACKGROUND_ALPHA = 200
private const val BORDER_ALPHA = 110
private const val ACCENT_SOFT_ALPHA = 40

// Fixed semantic hues; only their footing moves.
private val SUCCESS = Color(124, 224, 162)
private val DANGER = Color(255, 96, 96)

// Keeps derived colors off the pass/fail boundary; step for walking semantic colors.
private const val SEMANTIC_MARGIN = 1.1f
private const val SEMANTIC_STEP = 0.02f
private const val SEMANTIC_STEPS = 50

// Scrims are black in every theme.
private val OVERLAY_DIM = Color(0, 0, 0, 140)
private val TRACK = Color(0, 0, 0, 90)

private fun derive(bases: ThemeBases): Palette {
    val surface = bases.surface.toHsb()
    val text = bases.text.toHsb()

    // Hover/active lift toward the text color, working for dark and light themes. The background is always the darker.
    val lift = if (text.brightness >= surface.brightness) 1 else -1

    fun rung(step: Int, alpha: Int = 255): Color = Hsb(
        surface.hue,
        surface.saturation * SURFACE_FADE.pow(abs(step)),
        surface.brightness + SURFACE_STEP * lift * step,
    ).toColor(alpha)

    fun tier(level: Int): Color = Hsb(
        text.hue,
        text.saturation + (surface.saturation - text.saturation) * (1f - (1f - TEXT_TINT).pow(level)),
        surface.brightness + (text.brightness - surface.brightness) * TEXT_KEEP.pow(level),
    ).toColor()

    val background = Hsb(
        surface.hue,
        surface.saturation / SURFACE_FADE,
        surface.brightness - SURFACE_STEP,
    ).toColor(BACKGROUND_ALPHA)

    return Palette { slot ->
        when (slot) {
            ThemeSlot.BACKGROUND -> background
            ThemeSlot.SURFACE -> rung(0)
            ThemeSlot.SURFACE_HOVER -> rung(1)
            ThemeSlot.SURFACE_ACTIVE -> rung(2)
            ThemeSlot.BORDER -> rung(3, BORDER_ALPHA)
            ThemeSlot.OVERLAY_DIM -> OVERLAY_DIM
            ThemeSlot.TRACK -> TRACK
            ThemeSlot.ACCENT -> bases.accent
            ThemeSlot.ACCENT_SOFT -> with(bases.accent) { Color(red, green, blue, ACCENT_SOFT_ALPHA) }
            ThemeSlot.SUCCESS -> readable(SUCCESS, bases.surface, lift)
            ThemeSlot.DANGER -> readable(DANGER, bases.surface, lift)
            ThemeSlot.TEXT_PRIMARY -> tier(0)
            ThemeSlot.TEXT_SECONDARY -> tier(1)
            ThemeSlot.TEXT_MUTED -> tier(2)
            ThemeSlot.TEXT_DISABLED -> tier(3)
        }
    }
}

/** Walks a semantic hue off [surface] until it reads. Not the accent: player-chosen, so the validator just flags it. */
private fun readable(base: Color, surface: Color, lift: Int): Color {
    val target = SEMANTIC_CONTRAST * SEMANTIC_MARGIN
    val hsb = base.toHsb()
    val walk = generateSequence(hsb.brightness) { it + lift * SEMANTIC_STEP }
        .takeWhile { it in 0f..1f }
        .take(SEMANTIC_STEPS)
        .map { Hsb(hsb.hue, hsb.saturation, it).toColor() }
    // Best effort when the hue can't reach the target; the validator reports the rest.
    return walk.firstOrNull { contrastRatio(it, surface) >= target } ?: walk.lastOrNull() ?: base
}

private class Hsb(val hue: Float, val saturation: Float, val brightness: Float) {
    fun toColor(alpha: Int = 255): Color {
        val rgb = Color.HSBtoRGB(hue, saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))
        return Color((rgb and 0xFFFFFF) or (alpha shl 24), true)
    }
}

private fun Color.toHsb(): Hsb = Color.RGBtoHSB(red, green, blue, null).let { Hsb(it[0], it[1], it[2]) }
