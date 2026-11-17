package org.blackaddons.api.ui.theme

import org.blackaddons.api.ui.render.Font

/** Shared font handles for the host and addons; `.ttf`s ship under `assets/blackaddons/font/`. */
object Fonts {
    val regular = Font("assets/blackaddons/font/jetbrainsmono-regular.ttf")
    val medium = Font("assets/blackaddons/font/jetbrainsmono-medium.ttf")
    val bold = Font("assets/blackaddons/font/jetbrainsmono-bold.ttf")

    /** Proportional sans for long prose such as item lore, where a monospace face runs wide. */
    val sans = Font("assets/blackaddons/font/inter-regular.ttf")
    val sansBold = Font("assets/blackaddons/font/inter-bold.ttf")

    /** Vanilla-look numerals. */
    val minecraft = Font("assets/blackaddons/font/minecraft.ttf")

    val courier = Font("assets/blackaddons/font/courierprime-regular.ttf")

    /** Fallback for glyphs the others lack (Hypixel's ❤ ✎ ❈); Minecraft's own fallback too. */
    val unifont = Font("assets/blackaddons/font/unifont.ttf")
}
