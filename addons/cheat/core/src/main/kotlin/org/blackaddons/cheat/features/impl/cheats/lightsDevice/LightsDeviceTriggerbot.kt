package org.blackaddons.cheat.features.impl.cheats.lightsDevice

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.ServerTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.ClickRequest
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.ClientActionResult
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.util.ServerClock
import org.blackaddons.api.util.TickTimer
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.cheat.features.impl.cheats.lightsDevice.LightsDeviceTriggerbot.CONFIRM_TIMEOUT_TICKS

/**
 * Flips a solution lever that's still off when the crosshair is on it. Progress is read from the world, never counted.
 * Only an unconfirmed flip is remembered (clicking again would undo it), for [CONFIRM_TIMEOUT_TICKS] server ticks and
 * at least that long in real time.
 */
object LightsDeviceTriggerbot : Feature(
    name = "Lights Device Triggerbot",
    description = "Flips the six lights device levers of F7/M7 phase 3 when your crosshair is on one.",
) {

    override val section = Categories.Cheats.Dungeons

    private val clickDelay by number("Click Delay", default = 3.0, min = 1.0, max = 10.0, step = 1.0, unit = "ticks")
        .withDescription("Least time between two flips.")

    private const val CONFIRM_TIMEOUT_TICKS = 20

    private val delay = TickTimer()
    private val serverClock = ServerClock()
    private var click: ClickRequest? = null
    private var clickLever: BlockPos3? = null

    // Sent flips we haven't seen yet, with their deadlines.
    private val unconfirmed = HashMap<BlockPos3, ServerClock.Deadline>()

    override fun init() {
        // Pre: a click decided on Post misses the tick's click slot.
        register<ClientTickEvent.Pre>(EventPriority.LOW) { onTick() }
        register<ServerTickEvent> { onServerTick() }
        register<WorldChangeEvent> { reset() }
    }

    override fun onDisable() {
        super.onDisable()
        reset()
    }

    private fun onServerTick() {
        serverClock.tick()
        unconfirmed.values.removeIf(serverClock::passed)
    }

    private fun onTick() {
        delay.tick()
        if (LocationUtils.f7Phase != F7Phase.GOLDOR) {
            reset()
            return
        }
        unconfirmed.keys.removeIf { !LightsDevice.needsFlip(it) }

        val lever = aimedLever()
        if (lever != clickLever) cancelClick()
        if (lever == null || !delay.ready || click?.isPending == true) return

        clickLever = lever
        click = Platform.interaction.submit(ClientAction.Use, owner = this) { result ->
            if (result != ClientActionResult.SENT) return@submit
            unconfirmed[lever] = serverClock.after(CONFIRM_TIMEOUT_TICKS)
            delay.arm(clickDelay.value.toInt())
        }
    }

    // With silent aim live, the click target isn't the crosshair.
    private fun aimedLever(): BlockPos3? {
        if (Platform.screenHost.anyScreenOpen) return null
        val block = Platform.interaction.crosshairBlock ?: return null
        if (Platform.interaction.clickTargetBlock != block) return null
        return block.takeIf { it !in unconfirmed && LightsDevice.needsFlip(it) }
    }

    private fun cancelClick() {
        click?.cancel()
        click = null
        clickLever = null
    }

    private fun reset() {
        cancelClick()
        unconfirmed.clear()
        delay.clear()
    }
}
