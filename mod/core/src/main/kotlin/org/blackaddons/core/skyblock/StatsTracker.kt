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

/** Reads every action bar off the packet, so hiding the bar doesn't stop the stats. */
object StatsTracker : SkyblockStatsProvider {

    @Volatile
    override var current = ActionBarStats()
        private set

    private var vitalitySeenAtMs = 0L

    fun start() {
        EventBus.subscribe<ActionBarReceiveEvent> {
            if (!Platform.skyblock.onSkyblock) return@subscribe
            read(event.raw, System.currentTimeMillis())
        }
        EventBus.subscribe<WorldChangeEvent> {
            current = ActionBarStats()
            vitalitySeenAtMs = 0L
        }
    }

    internal fun read(raw: String, nowMs: Long) {
        val bar = ActionBar.stats(raw)
        if (bar.vitality != null) vitalitySeenAtMs = nowMs
        var next = current.merge(bar)
        // Vitality only shows while u hold an item that has it, so a merged old value would just stick around forever.
        if (nowMs - vitalitySeenAtMs >= VITALITY_GRACE_MS) next = next.copy(vitality = null, maxVitality = null)
        current = next
        ActionBar.skillGain(raw)?.let { EventBus.post(SkillXpEvent(it)) }
    }

    private const val VITALITY_GRACE_MS = 1_500L
}
