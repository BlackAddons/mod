package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.event.world.ContainerOpenEvent
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.util.TickTimer
import org.blackaddons.core.features.impl.chat.QuickJoin
import kotlin.time.Duration.Companion.seconds

object QuickJoinCheats : FeatureExtension(QuickJoin, group = "Cheats") {

    private val autoJoinConfirm by boolean("Auto Join Confirm", false)
        .withDescription("Confirms the Undersized party menu when you queue again while solo in a dungeon.")
    private val silentMenus by boolean("Silent Menus", true)
        .withDescription("Open the menus without showing them on screen.")
        .showIf { autoJoinConfirm.value }
    private val clickDelay by boolean("Click Delay", false)
        .withDescription("Wait a random time before clicking instead of clicking at once.")
        .showIf { autoJoinConfirm.value }
    private val minClickDelay by number("Min Click Delay", 50.0, 0.0, 500.0, 10.0, "ms")
        .withDescription("Shortest wait before each menu click.")
        .showIf { autoJoinConfirm.value && clickDelay.value }
    private val maxClickDelay by number("Max Click Delay", 100.0, 0.0, 500.0, 10.0, "ms")
        .withDescription("Longest wait before each menu click.")
        .showIf { autoJoinConfirm.value && clickDelay.value }

    private var menuId: Int? = null
    private var hidden = false
    private val delay = TickTimer()
    private val giveUp = TickTimer()
    private val catchCreating = TickTimer()

    override fun init() {
        register<ClientTickEvent.Pre> { onTick() }
        register<ContainerOpenEvent> {
            when {
                !catchCreating.ready && event.title == CREATING_TITLE -> event.cancelled = true
                silentMenus.value && event.title == UNDERSIZED && shouldConfirm() -> {
                    event.headless = true
                    hidden = true
                }
            }
        }
        register<MovementInputEvent>(EventPriority.LOWEST) {
            if (hidden && menuId != null) event.release()
        }
        register<WorldChangeEvent> { reset() }
    }

    private fun shouldConfirm(): Boolean =
        autoJoinConfirm.value && LocationUtils.inDungeon && Dungeon.team.others.isEmpty()

    private fun reset() {
        menuId = null
        hidden = false
        delay.clear()
        giveUp.clear()
        catchCreating.clear()
    }

    private fun onTick() {
        delay.tick()
        giveUp.tick()
        catchCreating.tick()

        val container = Platform.container
        val id = container.interactionId()
        if (id == null || container.title() != UNDERSIZED || !shouldConfirm()) {
            if (hidden && id != null && container.title() == UNDERSIZED) container.close()
            menuId = null
            hidden = false
            return
        }
        if (id != menuId) {
            menuId = id
            if (clickDelay.value) delay.armMillis(minClickDelay.value, maxClickDelay.value) else delay.clear()
            giveUp.arm(GIVE_UP..GIVE_UP)
        }
        if (!delay.ready) return

        val item = container.capture()?.raw?.getOrNull(CONFIRM_SLOT)?.let(Platform.items::describe)
        if (item?.name == UNDERSIZED) {
            container.clickSlot(CONFIRM_SLOT, button = 0, quickMove = false)
            catchCreating.arm(CATCH_WINDOW..CATCH_WINDOW)
        } else if (!hidden || !giveUp.ready) {
            return
        }
        container.close()
        menuId = null
        hidden = false
    }

    private const val UNDERSIZED = "Undersized party!"
    private const val CREATING_TITLE = "Creating instance...."
    private const val CONFIRM_SLOT = 13
    private val CATCH_WINDOW = 2.seconds
    private val GIVE_UP = 2.seconds
}
