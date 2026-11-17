package org.blackaddons.platform.impl.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.render.MenuBackgroundEvent
import org.blackaddons.api.event.render.MenuFramerateEvent
import org.blackaddons.blackskija.backend.common.SkijaCompositor
import org.blackaddons.platform.compat.displayRefreshRate

internal object MenuBackgrounds {

    /** True when a listener drew its own background, so the panorama gets skipped. */
    @JvmStatic
    fun replace(graphics: GuiGraphicsExtractor): Boolean {
        val event = MenuBackgroundEvent()
        EventBus.post(event)
        if (!event.replaced) return false
        // A stratum of its own, opened before the screen extracts its widgets, so all of them stay on top.
        SkijaCompositor.markScreenLayer(graphics)
        return true
    }

    @JvmStatic
    fun framerateLimit(vanilla: Int): Int {
        val event = MenuFramerateEvent()
        EventBus.post(event)
        return when (event.limit) {
            MenuFramerateEvent.Limit.VANILLA -> vanilla
            MenuFramerateEvent.Limit.UNLIMITED -> Options.UNLIMITED_FRAMERATE_CUTOFF
            MenuFramerateEvent.Limit.DISPLAY -> Minecraft.getInstance().displayRefreshRate() ?: vanilla
        }
    }
}
