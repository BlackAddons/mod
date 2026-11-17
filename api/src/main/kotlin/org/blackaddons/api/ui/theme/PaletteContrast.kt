package org.blackaddons.api.ui.theme

import java.awt.Color
import kotlin.math.pow

data class ContrastFailure(
    val foreground: ThemeSlot,
    val background: ThemeSlot,
    val ratio: Float,
    val required: Float,
)

/**
 * WCAG contrast checks a palette must pass or be refused. Derived palettes pass by construction; this catches
 * hand-pinned overrides. Empty = fine.
 */
fun Palette.contrastFailures(): List<ContrastFailure> = REQUIREMENTS.mapNotNull { (fg, bg, required) ->
    // Flattened first: translucent borders only exist over their surface, and the background only over the world.
    val under = composite(this[bg], Color.BLACK)
    val ratio = contrastRatio(composite(this[fg], under), under)
    if (ratio >= required) null else ContrastFailure(fg, bg, ratio, required)
}

internal const val SEMANTIC_CONTRAST = 3f

private data class Requirement(val foreground: ThemeSlot, val background: ThemeSlot, val required: Float)

// Set below the shipped palette's values. Primary text owes WCAG AA; faded tiers only have to stay visible.
private val REQUIREMENTS = listOf(
    Requirement(ThemeSlot.TEXT_PRIMARY, ThemeSlot.SURFACE, 4.5f),
    Requirement(ThemeSlot.TEXT_PRIMARY, ThemeSlot.BACKGROUND, 4.5f),
    Requirement(ThemeSlot.TEXT_SECONDARY, ThemeSlot.SURFACE, 3f),
    Requirement(ThemeSlot.TEXT_MUTED, ThemeSlot.SURFACE, 2f),
    Requirement(ThemeSlot.TEXT_DISABLED, ThemeSlot.SURFACE, 1.5f),
    Requirement(ThemeSlot.ACCENT, ThemeSlot.SURFACE, SEMANTIC_CONTRAST),
    Requirement(ThemeSlot.ACCENT, ThemeSlot.BACKGROUND, SEMANTIC_CONTRAST),
    Requirement(ThemeSlot.SUCCESS, ThemeSlot.SURFACE, SEMANTIC_CONTRAST),
    Requirement(ThemeSlot.DANGER, ThemeSlot.SURFACE, SEMANTIC_CONTRAST),
    Requirement(ThemeSlot.BORDER, ThemeSlot.SURFACE, 1.12f),
    Requirement(ThemeSlot.SURFACE, ThemeSlot.BACKGROUND, 1.08f),
    Requirement(ThemeSlot.SURFACE_HOVER, ThemeSlot.SURFACE, 1.08f),
)

internal fun composite(over: Color, under: Color): Color {
    if (over.alpha == 255) return over
    val a = over.alpha / 255f
    fun blend(o: Int, u: Int) = (o * a + u * (1f - a)).toInt().coerceIn(0, 255)
    return Color(blend(over.red, under.red), blend(over.green, under.green), blend(over.blue, under.blue))
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private fun relativeLuminance(color: Color): Float =
    0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)

private fun channel(value: Int): Float {
    val c = value / 255f
    return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
}
