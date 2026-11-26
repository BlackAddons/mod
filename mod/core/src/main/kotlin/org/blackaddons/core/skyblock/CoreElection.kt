package org.blackaddons.core.skyblock

import com.google.gson.Gson
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.dungeon.DungeonRunStartedEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.ElectionProvider
import org.blackaddons.api.skyblock.Mayor
import org.blackaddons.api.skyblock.MayorPerk
import org.blackaddons.api.skyblock.Minister
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

object CoreElection : ElectionProvider {

    private const val URL = "${Backend.HYPIXEL_URL}/v2/resources/skyblock/election"
    private val WORLD_REFRESH = 1.hours

    @Volatile
    override var mayor: Mayor? = null
        internal set

    @Volatile
    private var fetchedAt = TimeSource.Monotonic.markNow()
    private var started = false
    private val gson = Gson()

    fun start() {
        if (started) return
        started = true
        refresh()
        // Refreshing at a run start keeps Paul's score bonus exact. A world change only catches a new term.
        EventBus.subscribe<DungeonRunStartedEvent> { refresh() }
        EventBus.subscribe<WorldChangeEvent> {
            if (fetchedAt.elapsedNow() >= WORLD_REFRESH) refresh()
        }
    }

    /** Null when [body] has no mayor (a failed lookup), so we keep the mayor we already know. */
    internal fun parse(body: String): Mayor? = runCatching {
        gson.fromJson(body, ElectionJson::class.java)?.mayor?.toMayor()
    }.getOrNull()

    private fun refresh() {
        fetchedAt = TimeSource.Monotonic.markNow()
        OffThread.run(
            name = "election",
            work = { Http.get(URL, authenticated = true)?.takeIf { it.status == 200 }?.let { parse(it.body) } },
            onFailure = { null },
            onResult = { fetched -> fetched?.let { mayor = it } },
        )
    }

    private class ElectionJson(val mayor: MayorJson?)

    private class MayorJson(val key: String?, val name: String?, val perks: List<PerkJson?>?, val minister: MinisterJson?) {
        fun toMayor(): Mayor? = Mayor(
            key = key ?: return null,
            name = name ?: key,
            perks = perks.orEmpty().mapNotNull { it?.toPerk() },
            minister = minister?.toMinister(),
        )
    }

    private class MinisterJson(val key: String?, val name: String?, val perk: PerkJson?) {
        fun toMinister(): Minister? = Minister(key ?: return null, name ?: key, perk?.toPerk() ?: return null)
    }

    private class PerkJson(val name: String?, val description: String?) {
        fun toPerk(): MayorPerk? = MayorPerk(name ?: return null, description.orEmpty())
    }
}
