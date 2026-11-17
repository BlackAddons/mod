package org.blackaddons.core.dungeon

import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.dungeon.F7PhaseStartEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.BossBarEvent
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DualTime
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.core.log.Log

/**
 * Current F7 boss phase from the first line of each boss. Since SkyBlock 0.27.2 the Wither King is found by his boss
 * bar (NoammAddons and quoi did the same on 2026-10-06).
 */
internal object F7Boss {

    // Necron says a different first line on a party's first clear.
    private val OPENING_LINES = mapOf(
        "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" to F7Phase.MAXOR,
        "[BOSS] Storm: Pathetic Maxor, just like expected." to F7Phase.STORM,
        "[BOSS] Goldor: Who dares trespass into my domain?" to F7Phase.GOLDOR,
        "[BOSS] Necron: Finally, I heard so much about you. The Eye likes you very much." to F7Phase.NECRON,
        "[BOSS] Necron: You went further than any human before, congratulations." to F7Phase.NECRON,
    )
    private const val WITHER_KING_BAR = "wither king"
    private val SPEAKER = Regex("""^\[BOSS] ([^:]+):""")

    private val logger = Log.area("f7-boss")

    // Logs first lines and new bars to debug.log, so patch changes show up. Capped in case a bar has live health.
    private const val MAX_LOGGED_BARS = 16
    private val heardSpeakers = HashSet<String>()
    private val seenBars = HashSet<String>()

    var phase: F7Phase? = null
        private set

    private var started = false

    fun start() {
        if (started) return
        started = true
        EventBus.subscribe<ChatReceiveEvent> {
            if (!LocationUtils.hasF7Boss) return@subscribe
            val line = event.unformatted.trim()
            val speaker = SPEAKER.find(line)?.groupValues?.get(1)
            if (speaker != null && heardSpeakers.add(speaker)) {
                logger.debug("First line at tick {}: {}", DungeonTracker.currentTime, line)
            }
            OPENING_LINES[line]?.let(::enter)
        }
        EventBus.subscribe<BossBarEvent.Update> {
            if (!LocationUtils.hasF7Boss) return@subscribe
            if (seenBars.size < MAX_LOGGED_BARS && seenBars.add(event.name)) logger.debug("Boss bar at tick {}: '{}'", DungeonTracker.currentTime, event.name)
            if (LocationUtils.dungeonFloor == DungeonFloor.M7 && event.name.contains(WITHER_KING_BAR, ignoreCase = true)) enter(F7Phase.WITHER_KING)
        }
    }

    fun reset() {
        phase = null
        heardSpeakers.clear()
        seenBars.clear()
    }

    private fun enter(next: F7Phase) {
        if (phase == next) return
        phase = next
        logger.debug("Phase {} at tick {}", next, DungeonTracker.currentTime)
        EventBus.post(F7PhaseStartEvent(next, DualTime(DungeonTracker.currentTime)))
    }
}
