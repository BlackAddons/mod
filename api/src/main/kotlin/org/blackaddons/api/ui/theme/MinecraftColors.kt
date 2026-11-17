package org.blackaddons.api.ui.theme

import java.awt.Color

/**
 * The 16 vanilla `§0`-`§f` colors, exact values from `TextColor`. Use only when the color means what it means
 * in Minecraft; [Theme] is our own look.
 */
object MinecraftColors {

    val BLACK = Color(0x000000)
    val DARK_BLUE = Color(0x0000AA)
    val DARK_GREEN = Color(0x00AA00)
    val DARK_AQUA = Color(0x00AAAA)
    val DARK_RED = Color(0xAA0000)
    val DARK_PURPLE = Color(0xAA00AA)
    val GOLD = Color(0xFFAA00)
    val GRAY = Color(0xAAAAAA)
    val DARK_GRAY = Color(0x555555)
    val BLUE = Color(0x5555FF)
    val GREEN = Color(0x55FF55)
    val AQUA = Color(0x55FFFF)
    val RED = Color(0xFF5555)
    val LIGHT_PURPLE = Color(0xFF55FF)
    val YELLOW = Color(0xFFFF55)
    val WHITE = Color(0xFFFFFF)

    /** Case-insensitive; null if not a color code. */
    fun byCode(code: Char): Color? = BY_CODE["0123456789abcdef".indexOf(code.lowercaseChar()).takeIf { it >= 0 } ?: return null]

    private val BY_CODE = listOf(
        BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY,
        DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE,
    )
}
