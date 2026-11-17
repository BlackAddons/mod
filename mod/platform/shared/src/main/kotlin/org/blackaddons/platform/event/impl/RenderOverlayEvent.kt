package org.blackaddons.platform.event.impl

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.blackaddons.api.event.Event

/**
 * 26.x splits rendering into extract and draw, the HUD runs during extract. `Render.*` calls queue into BlackSkija's
 * batch, flushed in the draw phase.
 */
class RenderOverlayEvent(
    val context: GuiGraphicsExtractor,
    val tickDelta: Float,
) : Event()
