package org.blackaddons.api.skyblock.dungeon

import org.blackaddons.api.Platform

/** The F7/M7 terminal menus, by title. */
object TerminalMenu {

    private val TITLE_PREFIXES = listOf(
        "Correct all the panes!",
        "Change all to same color!",
        "Click in order!",
        "What starts with:",
        "Select all the",
        "Click the button on time!",
    )

    fun isMenu(title: String?): Boolean = title != null && TITLE_PREFIXES.any(title::startsWith)

    val isOpen: Boolean get() = isMenu(Platform.container.title())
}
