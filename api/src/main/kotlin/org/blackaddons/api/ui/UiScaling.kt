package org.blackaddons.api.ui

import org.blackaddons.api.HostApi
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.UiScaling.autoHud
import org.blackaddons.api.ui.UiScaling.autoInterface
import org.blackaddons.api.ui.UiScaling.interfaceScale

/**
 * Screen and HUD size multipliers, here because the `:api` toolkit reads them while drawing. Screens are
 * pinned to a reference GUI Scale, so [autoInterface] measures real pixels; the HUD is in Minecraft units, so
 * [autoHud] measures those. Both are 1 when nothing needs correcting.
 */
object UiScaling {

    private const val REFERENCE_HEIGHT = 1080f
    private const val REFERENCE_SCALED_HEIGHT = REFERENCE_HEIGHT / 2f

    /** Clamps on the automatic factor: below, text is unreadable; above, fixed modals stop fitting. */
    private const val MIN_AUTO = 0.5f
    private const val MAX_AUTO = 2f

    /** Read while drawing (not pushed) so a slider resizes its own screen without lag. 1 until the host registers. */
    @JvmStatic
    var interfaceScale: () -> Float = { 1f }
        @HostApi set

    /** Composed with each element's own scale. */
    @JvmStatic
    var hudScale: () -> Float = { 1f }
        @HostApi set

    /** Apply a screen-size factor on top of [interfaceScale]. */
    @JvmStatic
    var autoScale: () -> Boolean = { true }
        @HostApi set

    val interfaceFactor: Float get() = interfaceScale() * if (autoScale()) autoInterface() else 1f

    val hudFactor: Float get() = hudScale() * if (autoScale()) autoHud() else 1f

    /** Real pixels on purpose - the GUI Scale must not reach a normalized panel. Clamped so fixed modals still fit. */
    private fun autoInterface(): Float {
        val window = Platform.window
        val pixels = window.scaledHeight * window.guiScale
        if (pixels <= 0f) return 1f
        return (pixels / REFERENCE_HEIGHT).coerceIn(MIN_AUTO, MAX_AUTO)
    }

    /**
     * Minecraft units, not pixels, so the HUD keeps a constant share of the screen. The cost: it no longer
     * grows with GUI Scale; turning the setting off restores vanilla behavior.
     */
    private fun autoHud(): Float {
        val height = Platform.window.scaledHeight
        if (height <= 0f) return 1f
        return (height / REFERENCE_SCALED_HEIGHT).coerceIn(MIN_AUTO, MAX_AUTO)
    }
}
