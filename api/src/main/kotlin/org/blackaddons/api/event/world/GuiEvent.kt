package org.blackaddons.api.event.world

import org.blackaddons.api.event.Event

/**
 * A screen opened or closed, without Minecraft types (platform code can use `ScreenEvent`). [title] is
 * unformatted. Returning in-world fires [Close]; no-GUI -> no-GUI fires nothing.
 */
sealed class GuiEvent(val title: String, val isContainer: Boolean) : Event() {
    class Open(title: String, isContainer: Boolean) : GuiEvent(title, isContainer)
    class Close(title: String, isContainer: Boolean) : GuiEvent(title, isContainer)
}
