package org.blackaddons.core.features.impl.dungeons.soloclears

import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.dungeon.DungeonPlayerDeathEvent
import org.blackaddons.api.event.dungeon.DungeonRunEndedEvent
import org.blackaddons.api.event.dungeon.DungeonRunStartedEvent
import org.blackaddons.api.event.network.ServerTimeUpdateEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.RunTimer
import org.blackaddons.core.dungeon.ScoreCalculation
import org.blackaddons.core.soloclears.RunPartySnapshot
import org.blackaddons.core.soloclears.RunRecorder

object SoloClearTimer {
    const val PREFIX_RTA = "RTA: "
    const val PREFIX_IGT = "IGT: "
    const val PREFIX_LAG = "Lag: "

    const val PREVIEW_RTA = "RTA: 01:05.000"
    const val PREVIEW_IGT = "IGT: 01:02.700"
    const val PREVIEW_LAG = "Lag: 02.300"

    private val timer = RunTimer()

    val running: Boolean get() = timer.running
    val completed: Boolean get() = timer.completed
    val rtaMs: Long get() = timer.rtaMs
    val igtMs: Long get() = timer.igtMs
    val desyncMs: Long get() = timer.desyncMs

    val shouldRender: Boolean
        get() = LocationUtils.inDungeon && LocationUtils.isF7orM7 && (running || completed)

    fun init() {
        EventBus.subscribe<DungeonRunStartedEvent> {
            if (SoloClears.enabled && LocationUtils.isF7orM7 && RunPartySnapshot.looksSolo()) {
                startRun()
            }
        }
        EventBus.subscribe<DungeonPlayerDeathEvent> {
            if (running) reset()
        }
        EventBus.subscribe<DungeonRunEndedEvent> {
            if (running) completeRun()
        }
        EventBus.subscribe<ServerTimeUpdateEvent> {
            timer.onServerTimeUpdate(event.timestamp)
        }
        EventBus.subscribe<ClientTickEvent.Pre> {
            if (!LocationUtils.inDungeon) {
                if (running || completed) reset()
                return@subscribe
            }
            if (RunRecorder.recording && !running && !completed) {
                startRun()
            }
            if (running && (ScoreCalculation.targetScore >= 300 || ScoreCalculation.boardScore >= 300)) {
                completeRun()
            }
        }
        EventBus.subscribe<WorldChangeEvent> {
            reset()
        }
    }

    fun startRun() {
        timer.start()
    }

    fun completeRun() {
        timer.complete()
    }

    fun reset() {
        timer.reset()
    }

    fun formatTimeWithMinutes(ms: Long): String = org.blackaddons.api.util.formatTimeWithMinutes(ms)

    fun formatDesync(ms: Long): String = org.blackaddons.api.util.formatDesync(ms)
}
