package org.blackaddons.api.hud

import org.blackaddons.api.HostApi
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.theme.FontChoice
import org.blackaddons.api.ui.theme.Fonts

/**
 * Font for HUD text being drawn now: the element's override (published by [HudElement.renderElement]) or the
 * global default registered by the host.
 */
object HudFontContext {
    /** INHERIT when no HUD is drawing. */
    @JvmStatic
    var current: String = FontChoice.INHERIT.name
        internal set

    /** Supplied by the host's font settings; has a sane default for startup. */
    @JvmStatic
    var globalFont: () -> Font = { Fonts.regular }
        @HostApi set

    /** Supplied by the host. Null once the font is gone. */
    @JvmStatic
    var installedFont: (String) -> Font? = { null }
        @HostApi set

    val font: Font get() = fontOf(current)

    /** For text measured outside the element's draw. */
    fun fontOf(key: String): Font {
        val choice = FontChoice.byName(key) ?: return installedFont(key) ?: globalFont()
        return choice.font ?: globalFont()
    }
}
