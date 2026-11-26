package org.blackaddons.core.actions.execution

import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.dungeon.*
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.BossBarEvent
import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.core.actions.ActionDungeonEvent
import org.blackaddons.core.actions.WaitMode
import org.blackaddons.core.actions.handlers.ChatActionHandler
import org.blackaddons.core.actions.steps.WaitStep

/** Numbered chat lines, boss bars and dungeon moments, so WAIT steps can ask "anything matching since I started". */
internal object ActionSignals {

    data class Signal(val seq: Long, val mode: WaitMode, val text: String = "", val event: ActionDungeonEvent? = null)

    private const val KEPT = 64

    private val recent = ArrayDeque<Signal>()
    private val eventListeners = mutableListOf<(ActionDungeonEvent) -> Unit>()

    var lastSeq = 0L
        private set

    fun init() {
        EventBus.subscribe<ChatReceiveEvent> { record(WaitMode.CHAT, text = event.unformatted) }
        EventBus.subscribe<BossBarEvent.Update> { record(WaitMode.BOSS_BAR, text = event.name) }
        EventBus.subscribe<DungeonRunStartedEvent> { dungeon(ActionDungeonEvent.RUN_START) }
        EventBus.subscribe<DungeonDoorOpenedEvent> { if (event.type == DoorType.BLOOD) dungeon(ActionDungeonEvent.BLOOD_OPEN) }
        EventBus.subscribe<DungeonBossEnterEvent> { dungeon(ActionDungeonEvent.BOSS_ENTER) }
        EventBus.subscribe<F7PhaseStartEvent> { dungeon(event.phase.actionEvent) }
        EventBus.subscribe<DungeonRunEndedEvent> { dungeon(ActionDungeonEvent.RUN_END) }
        EventBus.subscribe<WorldChangeEvent> { recent.clear() }
    }

    fun onDungeonEvent(listener: (ActionDungeonEvent) -> Unit) {
        eventListeners += listener
    }

    fun matchedSince(seq: Long, step: WaitStep.Params): Boolean = recent.any { it.seq > seq && matches(step, it) }

    internal fun record(mode: WaitMode, text: String = "", event: ActionDungeonEvent? = null) {
        recent.addLast(Signal(++lastSeq, mode, text, event))
        while (recent.size > KEPT) recent.removeFirst()
    }

    private fun dungeon(event: ActionDungeonEvent) {
        record(WaitMode.DUNGEON_EVENT, event = event)
        eventListeners.forEach { it(event) }
    }

    private fun matches(step: WaitStep.Params, signal: Signal): Boolean {
        if (signal.mode != step.waitMode) return false
        return when (step.waitMode) {
            WaitMode.DUNGEON_EVENT -> signal.event == step.waitEvent
            WaitMode.CHAT, WaitMode.BOSS_BAR -> step.waitText.isNotBlank() && textMatches(step, signal.text)
            WaitMode.TIME, WaitMode.POSITION -> false
        }
    }

    private fun textMatches(step: WaitStep.Params, text: String): Boolean =
        if (step.waitRegex) ChatActionHandler.compiled(step.waitText)?.matcher(text)?.find() == true
        else text.containsIgnoreCase(step.waitText)

    private val F7Phase.actionEvent: ActionDungeonEvent
        get() = when (this) {
            F7Phase.MAXOR -> ActionDungeonEvent.MAXOR
            F7Phase.STORM -> ActionDungeonEvent.STORM
            F7Phase.GOLDOR -> ActionDungeonEvent.GOLDOR
            F7Phase.NECRON -> ActionDungeonEvent.NECRON
            F7Phase.WITHER_KING -> ActionDungeonEvent.WITHER_KING
        }
}
