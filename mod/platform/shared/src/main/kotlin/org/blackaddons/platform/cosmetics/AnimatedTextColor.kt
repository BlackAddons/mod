package org.blackaddons.platform.cosmetics

import org.blackaddons.api.util.color.ColorStop

/**
 * Animation state duck-typed onto `TextColor` by `TextColorMixin`. When set, `getValue()` returns a live
 * chroma/gradient color, so stored components (chat history) keep animating. Each animated character gets its own
 * `TextColor` with its own offset.
 */
interface AnimatedTextColor {
    fun baSetAnimated(animated: Boolean)

    fun baSetChroma(chroma: Boolean)

    fun baSetStops(stops: List<ColorStop>)

    fun baSetSpeed(speed: Float)

    fun baSetOffset(offset: Float)
}
