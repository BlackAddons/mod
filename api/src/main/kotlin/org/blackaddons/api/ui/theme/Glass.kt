package org.blackaddons.api.ui.theme

import org.blackaddons.api.util.color.fade
import org.blackaddons.api.util.color.lerpArgb

/**
 * The card looks our screens are built from, shared with the vanilla skins so both read one definition. Colors are
 * packed ARGB resolved from [Theme.palette] on every call, so a theme repaint applies live. `hover` runs 0..1.
 */
enum class Glass(
    val radius: Float,
    val borderWidth: Float,
    private val fillSlot: ThemeSlot,
    private val fillAlpha: Int? = null,
    private val washSlot: ThemeSlot? = null,
    private val washAlpha: Int? = null,
    private val borderSlot: ThemeSlot? = null,
    private val hoverBorderSlot: ThemeSlot? = null,
) {
    /** The ClickGUI window and modals. */
    WINDOW(Theme.RADIUS_LARGE, 0f, ThemeSlot.BACKGROUND),

    /** Inventory Buttons: an accent wash and border on hover. */
    BUTTON(
        Theme.RADIUS_SMALL, 1f, ThemeSlot.BACKGROUND,
        washSlot = ThemeSlot.ACCENT_SOFT, borderSlot = ThemeSlot.BORDER, hoverBorderSlot = ThemeSlot.ACCENT,
    ),

    /** Nearly opaque: it sits over a panel, not the world. */
    TOOLTIP(Theme.RADIUS_SMALL, 1f, ThemeSlot.BACKGROUND, fillAlpha = 240, borderSlot = ThemeSlot.BORDER),

    /** Profile Viewer cards and storage pages. */
    CARD(Theme.RADIUS, 0f, ThemeSlot.SURFACE, washSlot = ThemeSlot.SURFACE_HOVER),

    /** An item slot, with vanilla's white hover wash. */
    CELL(2f, 0f, ThemeSlot.BACKGROUND, fillAlpha = 170, washSlot = ThemeSlot.TEXT_PRIMARY, washAlpha = 128),

    /** Dims the world behind a full-screen overlay. */
    BACKDROP(0f, 0f, ThemeSlot.BACKGROUND, fillAlpha = 204),

    /** HUD panels over the world (tab list, scoreboard, hotbar): as translucent as a [CELL]. */
    HUD(Theme.RADIUS, 0f, ThemeSlot.BACKGROUND, fillAlpha = 170),
    ;

    val hasBorder: Boolean get() = borderSlot != null

    fun fill(): Int = argb(fillSlot, fillAlpha)

    /** Laid over [fill]; fully transparent when the style has no hover wash or [hover] is 0. */
    fun wash(hover: Float): Int {
        val slot = washSlot ?: return 0
        return fade(argb(slot, washAlpha), hover)
    }

    /** Fully transparent when the style has no border. */
    fun border(hover: Float): Int {
        val from = borderSlot ?: return 0
        val to = hoverBorderSlot ?: return argb(from, null)
        return lerpArgb(argb(from, null), argb(to, null), hover)
    }

    private companion object {
        const val RGB = 0xFFFFFF
		
        fun argb(slot: ThemeSlot, alpha: Int?): Int {
            val color = Theme.palette[slot].rgb
            return if (alpha == null) color else (alpha shl 24) or (color and RGB)
        }
    }
}
