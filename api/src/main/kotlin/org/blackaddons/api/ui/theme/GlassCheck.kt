package org.blackaddons.api.ui.theme

import org.blackaddons.api.util.color.fade
import org.blackaddons.api.util.color.lerpArgb

/**
 * The glass checkbox, one look for our screens and the vanilla skin. Colors are packed ARGB for a box that is [on]
 * (checked, eased 0..1) and hovered by [hover] (0..1), layered in this order: [glow] as a ring just outside the box,
 * then [base], [frost], [accent], the [edge] border, and the [mark] drawing on from its start through the elbow as [on]
 * rises.
 */
object GlassCheck {

    const val RADIUS = 3f
    const val DURATION_MS = 220L

    /** The mark's stroke width as a fraction of the box. */
    const val STROKE = 0.11f

    const val START_X = 0.22f
    const val START_Y = 0.53f
    const val ELBOW_X = 0.43f
    const val ELBOW_Y = 0.74f
    const val END_X = 0.78f
    const val END_Y = 0.27f

    fun glow(on: Float): Int = fade(Theme.ACCENT.rgb, GLOW * on)

    fun base(): Int = Theme.SURFACE.rgb

    fun frost(on: Float, hover: Float): Int = fade(Theme.TEXT_PRIMARY.rgb, (FROST + FROST_HOVER * hover) * (1f - on))

    fun accent(on: Float): Int = fade(Theme.ACCENT.rgb, on)

    fun edge(on: Float, hover: Float): Int =
        fade(lerpArgb(fade(Theme.TEXT_PRIMARY.rgb, EDGE), Theme.ACCENT.rgb, hover), 1f - on)

    fun mark(): Int = Theme.TEXT_PRIMARY.rgb

    private const val GLOW = 0.45f
    private const val FROST = 0.06f
    private const val FROST_HOVER = 0.08f
    private const val EDGE = 0.55f
}
