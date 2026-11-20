package org.blackaddons.platform.impl.gui

import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.blackaddons.api.gui.GuiBox

// Loaded through REI's rei_client entrypoint, so only when REI is installed.
class ReiCompat : REIClientPlugin {

    override fun registerExclusionZones(zones: ExclusionZones) {
        zones.register(AbstractContainerScreen::class.java) { screen: AbstractContainerScreen<*> ->
            MinecraftContainerOverlays.exclusionZones(screen).map(::entrySized)
        }
    }

    /*
     * REI only hides an entry when one of its corners is inside a zone, so a zone smaller than an entry, like the
     * pencil, can sit between the corners. Growing zones to one entry plus a pixel always catches a corner.
     * shortcut: assumes REI's default 18px entries, read REI's entry size setting if bigger ones matter.
     */
    private fun entrySized(box: GuiBox): Rectangle {
        val w = maxOf(box.w, MIN_ZONE)
        val h = maxOf(box.h, MIN_ZONE)
        return Rectangle(box.x - (w - box.w) / 2, box.y - (h - box.h) / 2, w, h)
    }

    private companion object {
        const val MIN_ZONE = 19
    }
}
