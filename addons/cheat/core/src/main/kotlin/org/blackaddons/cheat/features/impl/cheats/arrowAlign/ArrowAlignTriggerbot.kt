package org.blackaddons.cheat.features.impl.cheats.arrowAlign

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
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.util.ServerClock
import org.blackaddons.api.util.TickTimer
import org.blackaddons.api.world.ItemFrameSnapshot
import java.util.UUID

/**
 * Turns the arrow under the crosshair until it matches the layout. [FrameTurns] counts turns in flight, so clicks don't
 * have to wait for the server.
 */
object ArrowAlignTriggerbot : Feature(
    name = "Arrow Align Triggerbot",
    description = "Turns the arrows of the F7/M7 arrow align device into place while your crosshair is on them.",
) {

    override val section = Categories.Cheats.Dungeons

    private val clickDelay by number("Click Delay", default = 3.0, min = 1.0, max = 10.0, step = 1.0, unit = "ticks")
        .withDescription("Least time between two clicks.")

    /** Server ticks before a sent turn counts as lost. */
    private const val TURN_TIMEOUT_TICKS = 20
    private const val ARROW = "minecraft:arrow"

    private val delay = TickTimer()
    private var click: ClickRequest? = null
    private var clickFrame: UUID? = null
    private val serverClock = ServerClock()
    private val turns = HashMap<UUID, FrameTurns>()

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
        turns.values.forEach { it.expire(serverClock) }
    }

    private fun onTick() {
        delay.tick()
        if (LocationUtils.f7Phase != F7Phase.GOLDOR) {
            reset()
            return
        }
        val frame = aimedFrame()
        if (frame != clickFrame) cancelClick()
        if (frame == null || !delay.ready || click?.isPending == true) return

        val arrows = Platform.world.itemFrames(ArrowAlignBoard.area).filter { it.itemId == ARROW }
        val rotations = IntArray(ArrowAlignBoard.SIZE) { ArrowAlignBoard.NO_ARROW }
        for (arrow in arrows) {
            val index = ArrowAlignBoard.index(arrow.pos) ?: continue
            // Two frames in one spot: board is mid-rebuild, can't trust it.
            if (rotations[index] != ArrowAlignBoard.NO_ARROW) return
            rotations[index] = arrow.rotation
            turns.getOrPut(arrow.uuid) { FrameTurns(arrow.rotation) }.observe(arrow.rotation)
        }
        val solution = ArrowAlignBoard.solve(rotations) ?: return
        val index = arrows.firstOrNull { it.uuid == frame }?.let { ArrowAlignBoard.index(it.pos) } ?: return
        val state = turns[frame] ?: return
        val leave = if (index == heldBack(arrows, solution)) 1 else 0
        if (ArrowAlignBoard.clicksNeeded(state.predicted, solution[index]) <= leave) return

        clickFrame = frame
        click = Platform.interaction.submit(ClientAction.Use, owner = this) { result ->
            if (result != ClientActionResult.SENT) return@submit
            state.sent(serverClock.after(TURN_TIMEOUT_TICKS))
            delay.arm(clickDelay.value.toInt())
        }
    }

    private fun heldBack(arrows: List<ItemFrameSnapshot>, solution: IntArray): Int? {
        if (!LocationUtils.isP3Sim && Dungeon.run.bossPhase?.let { it < F7Phase.GOLDOR } != true) return null
        return ArrowAlignBoard.bottomLeft(
            arrows.mapNotNull { arrow ->
                val index = ArrowAlignBoard.index(arrow.pos) ?: return@mapNotNull null
                val predicted = turns[arrow.uuid]?.predicted ?: return@mapNotNull null
                index.takeIf { ArrowAlignBoard.clicksNeeded(predicted, solution[it]) > 0 }
            },
        )
    }

    // With silent aim live, the click target isn't the crosshair.
    private fun aimedFrame(): UUID? {
        if (Platform.screenHost.anyScreenOpen) return null
        val entity = Platform.interaction.crosshairEntityId ?: return null
        return entity.takeIf { Platform.interaction.clickTargetEntityId == it }
    }

    private fun cancelClick() {
        click?.cancel()
        click = null
        clickFrame = null
    }

    private fun reset() {
        cancelClick()
        turns.clear()
        delay.clear()
    }
}
