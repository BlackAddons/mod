package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.util.TickTimer

/**
 * Autismo property
 * Benjamin approved
 * Tel Aviv impressed
 */
object AutoBM : Feature(
    description = "Automatically upgrades Ballista Mechanic in Kuudra.",
    name = "Auto BM",
) {

    override val section = Categories.Cheats.Kuudra

    private val minClickDelay by number("Min Click Delay", 150.0, 50.0, 1000.0, 10.0, "ms")
        .withDescription("Shortest wait between upgrade clicks.")
        .group("Delays")
    private val maxClickDelay by number("Max Click Delay", 250.0, 50.0, 1000.0, 10.0, "ms")
        .withDescription("Longest wait between upgrade clicks.")
        .group("Delays")
    private val minFirstClickDelay by number("Min First Click Delay", 500.0, 150.0, 2000.0, 50.0, "ms")
        .withDescription("Shortest wait before the first upgrade click.")
        .group("Delays")
    private val maxFirstClickDelay by number("Max First Click Delay", 600.0, 150.0, 2000.0, 50.0, "ms")
        .withDescription("Longest wait before the first upgrade click.")
        .group("Delays")

    private var menuId: Int? = null
    private val cooldown = TickTimer()

    override fun init() {
        register<ClientTickEvent.Pre> { onTick() }
        // Read from the packet, so a chat cleaner can't leave us clicking without tokens.
        register<ChatReceiveEvent> { onChat(event.unformatted) }
    }

    override fun onDisable() {
        super.onDisable()
        resetState()
    }

    private fun onTick() {
        cooldown.tick()

        val container = Platform.container
        if (!Platform.player.isInGame || container.chestSlotCount() != MENU_SIZE) {
            resetState()
            return
        }

        val currentMenuId = container.interactionId() ?: return
        if (currentMenuId != menuId) {
            menuId = currentMenuId
            cooldown.armMillis(minFirstClickDelay.value, maxFirstClickDelay.value)
            return
        }
        if (!cooldown.ready) return

        val item = container.capture()?.raw?.getOrNull(BALLISTA_SLOT)
            ?.let(Platform.items::describe)
            ?: return
        if (!item.name.startsWith(BALLISTA_NAME)) return

        container.clickSlot(BALLISTA_SLOT, button = 0, quickMove = false)
        cooldown.armMillis(minClickDelay.value, maxClickDelay.value)
    }

    private fun onChat(message: String) {
        val trackedMenuId = menuId ?: return
        if (Platform.container.interactionId() != trackedMenuId || !message.startsWith(NO_TOKENS_MESSAGE)) return
        Platform.container.close()
        resetState()
    }

    private fun resetState() {
        menuId = null
        cooldown.clear()
    }

    private const val MENU_SIZE = 54
    private const val BALLISTA_SLOT = 13
    private const val BALLISTA_NAME = "Ballista Mechanic"
    private const val NO_TOKENS_MESSAGE = "You do not have enough tokens to upgrade this perk!"
}
