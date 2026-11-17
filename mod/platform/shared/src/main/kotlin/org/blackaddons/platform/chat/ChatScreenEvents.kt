package org.blackaddons.platform.chat

import net.minecraft.client.gui.screens.ChatScreen
import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.chat.ChatScreenRenderEvent
import org.blackaddons.blackskija.backend.common.SkijaCompositor
import org.blackaddons.platform.event.impl.RenderScreenBodyEvent
import org.blackaddons.platform.mixin.gui.ChatScreenAccessor

object ChatScreenEvents {

    fun start() {
        EventListener(RenderScreenBodyEvent::class.java, EventPriority.HIGH) {
            val screen = event.screen as? ChatScreen ?: return@EventListener
            val parts = screen as ChatScreenAccessor
            val covered = parts.`blackaddons$commandSuggestions`().isVisible ||
                parts.`blackaddons$input`().value.startsWith("/")
            EventBus.post(
                ChatScreenRenderEvent(
                    INPUT_INSET, screen.height - INPUT_BOTTOM, screen.width - INPUT_INSET * 2,
                    Platform.window.mouseX, Platform.window.mouseY, covered,
                ),
            )
            SkijaCompositor.markScreenLayer(event.graphics)
        }.register()
    }

    private const val INPUT_INSET = 2f
    private const val INPUT_BOTTOM = 14f
}
