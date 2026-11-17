package org.blackaddons.core.profile

import org.blackaddons.api.skyblock.profile.PlayerProfiles
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.profile.ProfileService.CACHE_TTL_MS
import org.blackaddons.core.profile.ProfileService.Entry
import org.blackaddons.core.profile.ProfileService.MAX_CACHED_PLAYERS
import java.util.UUID

/**
 * Profile lookup: resolve the account, check the cache, then go through the [ProfileSource] chain. A source that's down
 * falls through to the next one, while "no profiles" stops the chain. Results are cached per UUID for [CACHE_TTL_MS],
 * for at most [MAX_CACHED_PLAYERS] players. Blocking.
 */
internal object ProfileService {

    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    // Each entry keeps Hypixel's member JSON for `ProfileMember.raw`, which is a megabyte or two per player.
    private const val MAX_CACHED_PLAYERS = 32

    // Backend `/v1/skyblock/profiles`, parsed by [RawHypixelParser]. (Not Soopy: wrong at skill caps, fields missing.)
    private val sources: List<ProfileSource> = listOf(HypixelProfileSource)

    private class Entry(val loaded: LoadResult.Loaded, val expiresAt: Long)

    private val cache = object : LinkedHashMap<UUID, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Entry>): Boolean =
            size > MAX_CACHED_PLAYERS
    }

    fun load(name: String): LoadResult = traced(name) { load(LoadTrace.span("resolve") { identify(name) }) }

    fun load(uuid: UUID): LoadResult =
        traced(uuid.toString()) { cached(uuid) ?: load(LoadTrace.span("resolve") { MojangResolver.nameOf(uuid) }) }

    fun cachedProfiles(uuid: UUID): PlayerProfiles? = cached(uuid)?.profiles

    private fun load(who: MojangResolver.Resolution): LoadResult {
        val id = when (who) {
            is MojangResolver.Resolution.Found -> who.id
            MojangResolver.Resolution.NotFound -> return LoadResult.PlayerNotFound
            MojangResolver.Resolution.Unavailable -> return LoadResult.Unavailable
        }
        val uuid = Uuids.parse(id.uuid) ?: return LoadResult.PlayerNotFound

        cached(uuid)?.let {
            LoadTrace.note("from cache")
            return it
        }

        // Runs alongside the fetch, which takes way longer.
        val skin = ProfileWorkers.submit { MojangResolver.texturesProperty(id.uuid) }
        var sawUnavailable = false
        for (source in sources) {
            when (val result = source.fetch(id)) {
                is SourceResult.Ok -> {
                    LoadTrace.span("history") { TeammateHistory.remember(id.uuid, result.recent) }
                    // Avatar skin fetched here, so it gets cached with the profiles.
                    val textures = LoadTrace.span("skin wait") { runCatching { skin.get() }.getOrNull() }
                    val loaded = LoadResult.Loaded(result.profiles, textures)
                    store(uuid, loaded)
                    return loaded
                }
                SourceResult.Empty -> return LoadResult.NoProfiles(id.name)
                SourceResult.Unavailable -> sawUnavailable = true
            }
        }
        return if (sawUnavailable) LoadResult.Unavailable else LoadResult.NoProfiles(id.name)
    }

    /**
     * Uses this session's sightings before the network, because opened profiles were usually just on screen. Only wrong
     * after a rename we didn't see. Misses go to the proxy.
     */
    private fun identify(name: String): MojangResolver.Resolution {
        PlayerNames.sighting(name)?.let { seen ->
            return MojangResolver.Resolution.Found(MojangId(seen.uuid.dashless, seen.name))
        }
        // A player looked up a minute ago shouldn't have to wait on Mojang for their UUID again.
        loadedNamed(name)?.let { profiles ->
            return MojangResolver.Resolution.Found(MojangId(profiles.uuid.dashless, profiles.name))
        }
        return MojangResolver.resolve(name)
    }

    private fun traced(who: String, load: () -> LoadResult): LoadResult = LoadTrace.lookup(who, ::outcome, load)

    private fun outcome(result: LoadResult): String = when (result) {
        is LoadResult.Loaded -> "loaded"
        LoadResult.PlayerNotFound -> "no such player"
        is LoadResult.NoProfiles -> "no profiles"
        LoadResult.Unavailable -> "unavailable"
    }

    private fun loadedNamed(name: String): PlayerProfiles? = synchronized(cache) {
        val now = System.currentTimeMillis()
        cache.values.firstOrNull { it.expiresAt > now && it.loaded.profiles.name.equals(name, ignoreCase = true) }
            ?.loaded?.profiles
    }

    private fun cached(uuid: UUID): LoadResult.Loaded? = synchronized(cache) {
        val entry = cache[uuid] ?: return null
        if (System.currentTimeMillis() >= entry.expiresAt) {
            cache.remove(uuid)
            return null
        }
        entry.loaded
    }

    private fun store(uuid: UUID, loaded: LoadResult.Loaded) = synchronized(cache) {
        cache[uuid] = Entry(loaded, System.currentTimeMillis() + CACHE_TTL_MS)
    }
}

internal sealed interface LoadResult {
    class Loaded(val profiles: PlayerProfiles, val textures: SkinTextures?) : LoadResult
    object PlayerNotFound : LoadResult
    data class NoProfiles(val username: String) : LoadResult
    object Unavailable : LoadResult
}
