package org.blackaddons.platform.impl.gui
import net.minecraft.world.inventory.ContainerInput

/** Clicks via `ContainerInput` / `handleContainerInput`. */
object MinecraftOpenContainer : AbstractOpenContainer() {
    override fun sendClick(containerId: Int, slotIndex: Int, button: Int, quickMove: Boolean) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val input = if (quickMove) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
        gameMode.handleContainerInput(containerId, slotIndex, button, input, player)
    }
}
