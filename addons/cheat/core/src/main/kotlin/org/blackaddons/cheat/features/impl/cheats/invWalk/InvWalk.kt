package org.blackaddons.cheat.features.impl.cheats.invWalk

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatPacketEvent
import org.blackaddons.api.event.world.InventoryEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.DrivePriority
import org.blackaddons.api.player.Lease
import org.blackaddons.api.player.MoveKey

/*
 * Your movement keys keep working in the inventory until you click an item or send chat. That waits until the server
 * heard you stand still, and once the cursor is empty again a close tells the server the inventory is gone, so you
 * walk again.
 */
object InvWalk : Feature(
    description = "Walk with your inventory open. You stop for item clicks and walk again once your cursor is empty.",
    name = "InvWalk",
) {
    override val section = Categories.Cheats.General

    private val resumeDelay by number("Resume Delay", default = 1.0, min = 0.0, max = 5.0, step = 1.0, unit = "ticks")
        .withDescription("How long your cursor has to stay empty before you walk again. 0 walks again right away.")

    private val flow = InvWalkFlow()
    private var walk: Lease? = null

    private var sending = false

    override fun init() {
        register<ClientTickEvent.Pre> {
            if (walk?.isActive != true) walk = Platform.movement.drive(this@InvWalk, DrivePriority.LOW, ::steer)
            sync()
            if (flow.screen == null) return@register
            if (!flow.walking) Platform.movement.stopSprinting()
            val itemsClear = Platform.container.carriedItemRaw() == null && Platform.container.isCraftingGridEmpty()
            when (val step = flow.tick(Platform.movement.isStillForServer, itemsClear, resumeDelay.value.toInt())) {
                WalkStep.None -> Unit
                is WalkStep.Send -> sendingOwn {
                    step.clicks.forEach { Platform.container.click(it.slotId, it.button, it.kind) }
                    if (step.chat) Platform.chat.releaseHeld()
                }
                WalkStep.Close -> sendingOwn { Platform.container.closeSilently() }
            }
        }
        register<InventoryEvent.SlotClick>(EventPriority.LOWEST) {
            if (sending || event.cancelled) return@register
            sync()
            val click = HeldClick(event.slotId, event.button, event.kind)
            if (!flow.onClick(click, Platform.movement.isStillForServer)) event.cancelled = true
        }
        register<ChatPacketEvent> {
            if (sending) return@register
            sync()
            if (!flow.onChat(Platform.movement.isStillForServer)) event.held = true
        }
        register<InventoryEvent.Close>(EventPriority.LOWEST) {
            if (sending || event.cancelled) return@register
            if (flow.keepCloseBack()) event.cancelled = true
        }
        register<WorldChangeEvent> { reset() }
    }

    override fun onDisable() {
        super.onDisable()
        walk?.release()
        walk = null
        reset()
    }

    // Drops the held clicks with their screen, but still sends the held chat.
    private fun reset() {
        flow.reset()
        Platform.chat.releaseHeld()
    }

    // Follows the open screen. It opens mid-tick, after ClientTickEvent.Pre, so every entry point calls this.
    private fun sync() {
        val screen = openScreen()
        if (screen == flow.screen) return
        if (screen == null) reset() else flow.open(screen)
    }

    private fun openScreen(): WalkScreen? = WalkScreen.INVENTORY.takeIf { Platform.container.isPlayerInventoryOpen() }

    private fun steer(): Set<MoveKey>? {
        sync()
        if (flow.screen == null) return null
        if (!flow.walking) return emptySet()
        return MoveKey.entries.filterTo(HashSet(), Platform.movement::isKeyDown)
    }

    private inline fun sendingOwn(block: () -> Unit) {
        sending = true
        try {
            block()
        } finally {
            sending = false
        }
    }
}
