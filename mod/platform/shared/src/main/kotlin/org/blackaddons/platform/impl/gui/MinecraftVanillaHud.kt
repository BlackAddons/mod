package org.blackaddons.platform.impl.gui

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.resources.Identifier
import org.blackaddons.api.hud.VanillaHud
import org.blackaddons.api.hud.VanillaHudPart
import org.blackaddons.platform.Installable
import java.util.EnumMap
import java.util.concurrent.CopyOnWriteArrayList

object MinecraftVanillaHud : VanillaHud, Installable {

    private val conditions = EnumMap<VanillaHudPart, MutableList<() -> Boolean>>(VanillaHudPart::class.java).apply {
        VanillaHudPart.entries.forEach { put(it, CopyOnWriteArrayList()) }
    }

    override fun hideWhen(part: VanillaHudPart, hidden: () -> Boolean) {
        conditions.getValue(part) += hidden
    }

    // Every part gets wrapped up front, because the registry is only meant to be edited during client init.
    override fun install() {
        for (part in VanillaHudPart.entries) {
            val hiders = conditions.getValue(part)
            HudElementRegistry.replaceElement(idOf(part)) { original ->
                HudElement { graphics, delta ->
                    if (hiders.none { it() }) original.extractRenderState(graphics, delta)
                }
            }
        }
    }

    private fun idOf(part: VanillaHudPart): Identifier = when (part) {
        VanillaHudPart.MISC_OVERLAYS -> VanillaHudElements.MISC_OVERLAYS
        VanillaHudPart.CROSSHAIR -> VanillaHudElements.CROSSHAIR
        VanillaHudPart.SPECTATOR_MENU -> VanillaHudElements.SPECTATOR_MENU
        VanillaHudPart.HOTBAR -> VanillaHudElements.HOTBAR
        VanillaHudPart.ARMOR -> VanillaHudElements.ARMOR_BAR
        VanillaHudPart.HEALTH -> VanillaHudElements.HEALTH_BAR
        VanillaHudPart.FOOD -> VanillaHudElements.FOOD_BAR
        VanillaHudPart.AIR -> VanillaHudElements.AIR_BAR
        VanillaHudPart.MOUNT_HEALTH -> VanillaHudElements.MOUNT_HEALTH
        VanillaHudPart.INFO_BAR -> VanillaHudElements.INFO_BAR
        VanillaHudPart.EXPERIENCE_LEVEL -> VanillaHudElements.EXPERIENCE_LEVEL
        VanillaHudPart.HELD_ITEM_NAME -> VanillaHudElements.HELD_ITEM_TOOLTIP
        VanillaHudPart.SPECTATOR_TOOLTIP -> VanillaHudElements.SPECTATOR_TOOLTIP
        VanillaHudPart.STATUS_EFFECTS -> VanillaHudElements.MOB_EFFECTS
        VanillaHudPart.BOSS_BARS -> VanillaHudElements.BOSS_BAR
        VanillaHudPart.SLEEP -> VanillaHudElements.SLEEP
        VanillaHudPart.DEMO_TIMER -> VanillaHudElements.DEMO_TIMER
        VanillaHudPart.SCOREBOARD -> VanillaHudElements.SCOREBOARD
        VanillaHudPart.ACTION_BAR -> VanillaHudElements.OVERLAY_MESSAGE
        VanillaHudPart.TITLE -> VanillaHudElements.TITLE_AND_SUBTITLE
        VanillaHudPart.CHAT -> VanillaHudElements.CHAT
        VanillaHudPart.PLAYER_LIST -> VanillaHudElements.PLAYER_LIST
        VanillaHudPart.SUBTITLES -> VanillaHudElements.SUBTITLES
    }
}
