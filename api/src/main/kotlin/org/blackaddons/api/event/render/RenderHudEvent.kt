package org.blackaddons.api.event.render

import org.blackaddons.api.event.Event

/**
 * Once per frame in the HUD pass, for drawing with `Render.*` outside `hudElement {}`. The MC-typed
 * `RenderOverlayEvent` gives raw `GuiGraphics` but ties you to one version.
 */
class RenderHudEvent(val tickDelta: Float) : Event()
