package org.blackaddons.core.render

/**
 * World-render preferences for the platform `WorldRenderer`, read each frame. Features register suppliers, so the
 * renderer imports no features. Defaults match the owners' defaults.
 */
object WorldRenderPrefs {
    /** 0 means static. Owned by `Actions`. */
    @Volatile var beaconSpeed: () -> Double = { 1.0 }

    @Volatile var worldFont: () -> WorldTextFont = { WorldTextFont.CUSTOM }

    @Volatile var waypointDepthCheck: () -> Boolean = { true }
}

enum class WorldTextFont { VANILLA, CUSTOM }
