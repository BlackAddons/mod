package org.blackaddons.platform.impl.gui
import net.minecraft.world.inventory.ContainerInput
import org.blackaddons.api.event.world.ClickKind

/** Clicks via `ContainerInput` / `handleContainerInput`. */
object MinecraftOpenContainer : AbstractOpenContainer() {
    override fun sendClick(containerId: Int, slotIndex: Int, button: Int, quickMove: Boolean) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val input = if (quickMove) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
        gameMode.handleContainerInput(containerId, slotIndex, button, input, player)
    }

    override fun sendInput(containerId: Int, slotIndex: Int, button: Int, kind: ClickKind) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val input = when (kind) {
            ClickKind.PICKUP -> ContainerInput.PICKUP
            ClickKind.QUICK_MOVE -> ContainerInput.QUICK_MOVE
            ClickKind.SWAP -> ContainerInput.SWAP
            ClickKind.CLONE -> ContainerInput.CLONE
            ClickKind.THROW -> ContainerInput.THROW
            ClickKind.QUICK_CRAFT -> ContainerInput.QUICK_CRAFT
            ClickKind.PICKUP_ALL -> ContainerInput.PICKUP_ALL
            ClickKind.UNKNOWN -> return
        }
        gameMode.handleContainerInput(containerId, slotIndex, button, input, player)
    }
}
