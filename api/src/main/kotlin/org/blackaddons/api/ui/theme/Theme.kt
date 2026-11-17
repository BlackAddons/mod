package org.blackaddons.api.ui.theme

import org.blackaddons.api.HostApi
import java.awt.Color
import java.util.concurrent.ThreadLocalRandom

/** UI palette and metrics. Colors follow the theme; metrics are `const`: moving them breaks widget measurement. */
object Theme {

    /** Assigning repaints the UI next frame, so editors preview live. */
    @Volatile
    var palette: Palette = ThemeSpec.MIDNIGHT.resolve()
        @HostApi set

    val OVERLAY_DIM: Color get() = palette[ThemeSlot.OVERLAY_DIM]

    val BACKGROUND: Color get() = palette[ThemeSlot.BACKGROUND]
    val SURFACE: Color get() = palette[ThemeSlot.SURFACE]
    val SURFACE_HOVER: Color get() = palette[ThemeSlot.SURFACE_HOVER]
    val SURFACE_ACTIVE: Color get() = palette[ThemeSlot.SURFACE_ACTIVE]
    val BORDER: Color get() = palette[ThemeSlot.BORDER]

    /** Translucent so it reads on any surface. */
    val TRACK: Color get() = palette[ThemeSlot.TRACK]

    /** Brand gradient ends. Not theme colors - no palette repaints them. */
    val BRAND_FROM = Color(0x9F42AD)
    val BRAND_TO = Color(0x753FD1)

    val ACCENT: Color get() = palette[ThemeSlot.ACCENT]
    val ACCENT_SOFT: Color get() = palette[ThemeSlot.ACCENT_SOFT]
    val SUCCESS: Color get() = palette[ThemeSlot.SUCCESS]
    val DANGER: Color get() = palette[ThemeSlot.DANGER]

    val TEXT_PRIMARY: Color get() = palette[ThemeSlot.TEXT_PRIMARY]
    val TEXT_SECONDARY: Color get() = palette[ThemeSlot.TEXT_SECONDARY]
    val TEXT_MUTED: Color get() = palette[ThemeSlot.TEXT_MUTED]
    val TEXT_DISABLED: Color get() = palette[ThemeSlot.TEXT_DISABLED]

    const val PADDING_SMALL = 4f
    const val PADDING = 8f
    const val PADDING_LARGE = 16f

    const val RADIUS_SMALL = 4f
    const val RADIUS = 8f
    const val RADIUS_LARGE = 12f

    const val SIDEBAR_WIDTH = 150f
    const val TAB_HEIGHT = 28f
    const val FEATURE_ROW_HEIGHT = 30f
    const val SETTING_ROW_HEIGHT = 22f

    const val FONT_TITLE = 15f
    const val FONT_BODY = 12f
    const val FONT_SMALL = 10f

    const val ANIM_FAST = 200
    const val ANIM_NORMAL = 380
    const val ANIM_HOVER = 180

    /** Caret, selection, per-character tweens. */
    const val ANIM_TEXT = 120

    /** For values chasing a moving target (slider fill, scroll). Smaller settles faster. */
    const val ANIM_DECAY_HALFLIFE = 0.05f

    /** Per wheel notch, for every scrollable surface. */
    const val SCROLL_STEP = 18f

    fun randomColor(saturation: Float = 0.8f, brightness: Float = 0.95f): Color {
        val hue = ThreadLocalRandom.current().nextFloat()
        return Color.getHSBColor(hue, saturation, brightness)
    }

    fun randomColorRgb(saturation: Float = 0.8f, brightness: Float = 0.95f): Int =
        randomColor(saturation, brightness).rgb and 0xFFFFFF

    fun Color.withAlpha(alpha: Int): Color = Color(red, green, blue, alpha.coerceIn(0, 255))
    fun Color.withAlpha(alpha: Float): Color = withAlpha((alpha * 255).toInt())
}
