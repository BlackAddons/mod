package org.blackaddons.platform.event.impl

import net.minecraft.client.gui.screens.Screen
import org.blackaddons.api.event.Event

sealed class ScreenEvent(val screen: Screen?) : Event() {
    class Open(screen: Screen?) : ScreenEvent(screen)
    class Close(screen: Screen?) : ScreenEvent(screen)
}
