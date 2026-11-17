package org.blackaddons.core.skyblock

import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.ServerTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.skyblock.InvincibilityProcEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.player.InventorySlots
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonPlayer
import org.blackaddons.api.skyblock.player.InvincibilityItem
import org.blackaddons.api.skyblock.player.InvincibilityProvider
import org.blackaddons.api.util.inWholeTicks
import org.blackaddons.api.util.secondsToTicks
import org.blackaddons.api.util.stripFormatting

/**
 * Starts a cooldown on each proc line, then posts [InvincibilityProcEvent]. The cooldowns count down on server ticks.
 */
object InvincibilityTracker : InvincibilityProvider {

    private val cooldowns = IntArray(InvincibilityItem.entries.size)
    private val COOLDOWN_LORE = Regex("""^Cooldown: ([\d.]+)s$""")

    private const val MAGE_BASE_REDUCTION = 0.25
    private const val MAGE_LEVELS_PER_PERCENT = 2
    private const val SOLO_MAGE_FACTOR = 2

    fun start() {
        EventBus.subscribe<ChatReceiveEvent> { onChat(event.unformatted) }
        EventBus.subscribe<ServerTickEvent> { tick() }
        EventBus.subscribe<WorldChangeEvent> { cooldowns.fill(0) }
    }

    override fun cooldownTicks(item: InvincibilityItem): Int = cooldowns[item.ordinal]

    override val wornMask: InvincibilityItem?
        get() {
            val helmet = Platform.player.inventoryItem(InventorySlots.HEAD) ?: return null
            return InvincibilityItem.entries.firstOrNull { it.mask?.invoke(helmet) == true }
        }

    internal fun onChat(unformatted: String) {
        val item = InvincibilityItem.fromMessage(unformatted) ?: return
        cooldowns[item.ordinal] = cooldownFor(item)
        EventBus.post(InvincibilityProcEvent(item))
    }

    internal fun tick() {
        for (i in cooldowns.indices) if (cooldowns[i] > 0) cooldowns[i]--
    }

    private fun cooldownFor(item: InvincibilityItem): Int {
        val baseTicks = item.cooldown.inWholeTicks.toInt()
        val mask = item.mask ?: return baseTicks
        val lore = Platform.player.inventoryItem(InventorySlots.HEAD)?.takeIf(mask)?.loreLines.orEmpty()
        val seconds = lore.firstNotNullOfOrNull { COOLDOWN_LORE.matchEntire(it.stripFormatting().trim())?.groupValues?.get(1)?.toDoubleOrNull() }
        val ticks = seconds?.secondsToTicks ?: baseTicks
        val team = Dungeon.team
        return (ticks * mageMultiplier(team.self, team.players.count { it.dungeonClass == DungeonClass.MAGE })).toInt()
    }

    internal fun mageMultiplier(self: DungeonPlayer?, mages: Int): Double {
        if (self?.dungeonClass != DungeonClass.MAGE) return 1.0
        val levelPart = (self.classLevel / MAGE_LEVELS_PER_PERCENT) / 100.0
        return 1 - MAGE_BASE_REDUCTION - levelPart * if (mages == 1) SOLO_MAGE_FACTOR else 1
    }
}
