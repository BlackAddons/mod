package org.blackaddons.core.skyblock

import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ActionBarReceiveEvent
import org.blackaddons.api.event.skyblock.SkillXpEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.player.ActionBar
import org.blackaddons.api.skyblock.player.ActionBarStats
import org.blackaddons.api.skyblock.player.SkyblockStatsProvider
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Reads every action bar off the packet, so hiding the bar doesn't stop the stats. */
object StatsTracker : SkyblockStatsProvider {

    @Volatile
    override var current = ActionBarStats()
        private set

    internal var clock: TimeSource.WithComparableMarks = TimeSource.Monotonic
    private var vitalitySeenAt: ComparableTimeMark? = null

    fun start() {
        EventBus.subscribe<ActionBarReceiveEvent> {
            if (!Platform.skyblock.onSkyblock) return@subscribe
            read(event.raw)
        }
        EventBus.subscribe<WorldChangeEvent> {
            current = ActionBarStats()
            vitalitySeenAt = null
        }
    }

    internal fun read(raw: String) {
        val bar = ActionBar.stats(raw)
        if (bar.vitality != null) vitalitySeenAt = clock.markNow()
        var next = current.merge(bar)
        // Vitality only shows while u hold an item that has it, so a merged old value would just stick around forever.
        if (vitalitySeenAt?.let { it.elapsedNow() < VITALITY_GRACE } != true) next = next.copy(vitality = null, maxVitality = null)
        current = next
        ActionBar.skillGain(raw)?.let { EventBus.post(SkillXpEvent(it)) }
    }

    private val VITALITY_GRACE = 1_500.milliseconds
}
