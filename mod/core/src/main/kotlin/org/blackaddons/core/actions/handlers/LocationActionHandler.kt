package org.blackaddons.core.actions.handlers

import org.blackaddons.api.Platform
import org.blackaddons.api.event.*
import org.blackaddons.api.event.dungeon.F7PhaseStartEvent
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.util.ServerClock
import org.blackaddons.api.util.secondsToTicks
import org.blackaddons.core.actions.ActionDungeonEvent
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.LocationAction
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.actions.execution.ActionSignals
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal object LocationActionHandler {

    private val matchingStates = mutableMapOf<UUID, Boolean>()
    private val lastTriggered = mutableMapOf<UUID, TimeSource.Monotonic.ValueTimeMark>()
    private var worldGeneration = 0L

    internal var serverClock = ServerClock()
    private var serverDelayed = listOf<Pair<LocationAction, ServerClock.Deadline>>()

    internal var phase: F7Phase? = null
        private set
    private var phaseStart = 0L

    internal val phaseTicks: Long get() = serverClock.ticks - phaseStart

    fun init() {
        EventBus.subscribe<ClientTickEvent.Post> { onTick() }
        EventBus.subscribe<ServerTickEvent> { onServerTick() }
        EventBus.subscribe<F7PhaseStartEvent> {
            phase = event.phase
            phaseStart = serverClock.ticks
        }
        EventBus.subscribe<WorldChangeEvent> { reset() }
        ActionSignals.onDungeonEvent(::onDungeonEvent)
    }

    fun reset() {
        worldGeneration++
        matchingStates.clear()
        lastTriggered.clear()
        serverDelayed = emptyList()
        phase = null
    }

    internal fun pendingTicks(): List<Pair<LocationAction, Long>> =
        serverDelayed.map { (action, at) -> action to (at.tick - serverClock.ticks).coerceAtLeast(0L) }

    private fun onTick() {
        if (!Platform.player.isInGame || ActionStorage.locationActions.isEmpty()) return
        val entered = ActionStorage.locationActions.filter { action ->
            val isMatching = action.matchesLocation() && ActionStorage.inActiveGroup(ActionStorage.locationGroups, action)
            val wasMatching = matchingStates.put(action.id, isMatching) ?: false
            action.triggerEvent == null && isMatching && !wasMatching
        }
        fire(entered)
    }

    internal fun onServerTick() {
        serverClock.tick()
        if (serverDelayed.isEmpty()) return
        val (due, waiting) = serverDelayed.partition { serverClock.passed(it.second) }
        serverDelayed = waiting
        ActionExecutor.executeAll(due.map { it.first }.filter { it.matchesLocation() }.map(LocationAction::actions))
    }

    // Event triggers still respect their location filters.
    internal fun onDungeonEvent(event: ActionDungeonEvent) {
        if (!Platform.player.isInGame) return
        fire(ActionStorage.locationActions.filter {
            it.triggerEvent == event && it.matchesLocation() && ActionStorage.inActiveGroup(ActionStorage.locationGroups, it)
        })
    }

    internal fun fire(actions: List<LocationAction>) {
        if (actions.isEmpty()) return
        val now = TimeSource.Monotonic.markNow()
        val immediate = mutableListOf<LocationAction>()
        val delayed = mutableMapOf<Int, MutableList<LocationAction>>()
        for (action in actions) {
            if (lastTriggered[action.id]?.let { now - it < action.cooldownSeconds.toDouble().seconds } == true) continue
            lastTriggered[action.id] = now
            if (action.delayServerTicks > 0) {
                serverDelayed += action to serverClock.after(action.delayServerTicks)
                continue
            }
            val delayTicks = action.delaySeconds.secondsToTicks
            if (delayTicks > 0) delayed.getOrPut(delayTicks, ::mutableListOf).add(action) else immediate.add(action)
        }

        ActionExecutor.executeAll(immediate.map(LocationAction::actions))
        for ((delayTicks, group) in delayed) {
            val scheduledGeneration = worldGeneration
            Platform.scheduler.schedule(delayTicks) {
                if (scheduledGeneration != worldGeneration) return@schedule
                ActionExecutor.executeAll(group.filter { it.matchesLocation() }.map(LocationAction::actions))
            }
        }
    }
}
