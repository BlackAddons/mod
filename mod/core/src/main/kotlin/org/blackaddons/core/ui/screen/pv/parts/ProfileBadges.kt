package org.blackaddons.core.ui.screen.pv.parts
import org.blackaddons.api.skyblock.profile.ProfileMode
import org.blackaddons.api.ui.theme.MinecraftColors
import org.blackaddons.api.ui.widget.dropdown.Dropdown

/**
 * Gamemode badges in Minecraft's colors ([MinecraftColors]), not the theme's. Drawn with Unifont so every symbol is
 * covered.
 */
internal object ProfileBadges {

    fun gamemodeIcon(mode: ProfileMode): Dropdown.Icon? = when (mode) {
        ProfileMode.IRONMAN -> Dropdown.Icon("♲", MinecraftColors.GRAY)
        ProfileMode.BINGO -> Dropdown.Icon("Ⓑ", MinecraftColors.DARK_PURPLE)
        ProfileMode.STRANDED -> Dropdown.Icon("☀", MinecraftColors.GREEN)
        ProfileMode.NORMAL, ProfileMode.OTHER -> null
    }
}
