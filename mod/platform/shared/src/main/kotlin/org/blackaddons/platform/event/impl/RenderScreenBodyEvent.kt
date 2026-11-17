package org.blackaddons.platform.event.impl

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import org.blackaddons.api.event.Event

/**
 * After a vanilla [Screen] extracts its widgets, before its tooltip. Draws land on top like [RenderScreenEvent] unless
 * the listener calls `SkijaCompositor.markScreenLayer(graphics)`, which puts them under the tooltip.
 */
internal class RenderScreenBodyEvent(
    val screen: Screen,
    val graphics: GuiGraphicsExtractor,
    val mouseX: Int,
    val mouseY: Int,
) : Event()
