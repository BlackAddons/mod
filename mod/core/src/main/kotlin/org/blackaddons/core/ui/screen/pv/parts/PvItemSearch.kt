package org.blackaddons.core.ui.screen.pv.parts

import org.blackaddons.api.skyblock.profile.ProfileItem
import org.blackaddons.api.util.concurrent.LatestOnly
import org.blackaddons.api.util.concurrent.OffThread
import java.util.Locale

/**
 * Finds items by name or Skyblock id across every container of a profile, so "where is my Spirit Sceptre" is one query
 * instead of opening each backpack. Names are read once per inventory, off the client thread, because reading a
 * thousand stacks takes a moment.
 */
internal class PvItemSearch<L : Any> {

    /** One container: where it is, and its slots. */
    class Place<L>(val location: L, val slots: List<ProfileItem?>)

    /** The items of one container that match. */
    class Hit<L>(val location: L, val items: List<ProfileItem>)

    private class Entry<L>(val location: L, val item: ProfileItem, val name: String, val id: String)

    @Volatile private var index: List<Entry<L>>? = null
    private var indexedFrom: Any? = null
    private val builds = LatestOnly()

    var query: String = ""
        private set
    private var needle = ""
    private var cachedEntries: List<Entry<L>>? = null
    private var cachedNeedle = ""
    private var cachedHits = emptyList<Hit<L>>()
    private var cachedMatches = emptySet<ProfileItem>()

    val active: Boolean get() = needle.isNotEmpty()

    /** False while the names are still being read. */
    val ready: Boolean get() = index != null

    fun reset() {
        builds.cancel()
        index = null
        indexedFrom = null
        setQuery("")
    }

    fun setQuery(text: String) {
        query = text
        needle = text.trim().lowercase(Locale.ROOT)
    }

    /** Reads the names of [places] the first time a search runs on [source], later calls with it do nothing. */
    fun index(source: Any, places: () -> List<Place<L>>) {
        if (indexedFrom === source || !active) return
        indexedFrom = source
        index = null
        OffThread.run<List<Entry<L>>>(
            name = "pv-item-search",
            work = {
                places().flatMap { place ->
                    place.slots.filterNotNull().mapNotNull { item ->
                        val info = item.info ?: return@mapNotNull null
                        Entry(place.location, item, info.name.lowercase(Locale.ROOT), info.skyblockId.orEmpty().lowercase(Locale.ROOT))
                    }
                }
            },
            onFailure = { emptyList() },
            onResult = builds.next { index = it },
        )
    }

    /** Matching items grouped by container, in the order the containers were given. */
    fun hits(): List<Hit<L>> {
        val entries = index ?: return emptyList()
        if (cachedEntries !== entries || cachedNeedle != needle) {
            cachedEntries = entries
            cachedNeedle = needle
            val found = if (needle.isEmpty()) emptyList() else entries.filter(::matches)
            cachedHits = found.groupBy { it.location }.map { (location, items) -> Hit(location, items.map { it.item }) }
            cachedMatches = found.mapTo(HashSet()) { it.item }
        }
        return cachedHits
    }

    /** Whether the current search found [item]. False until the names have been read. */
    fun matches(item: ProfileItem): Boolean {
        hits()
        return item in cachedMatches
    }

    // An id is written with underscores, so "spirit sceptre" finds SPIRIT_SCEPTRE too.
    private fun matches(entry: Entry<L>): Boolean =
        needle in entry.name || needle.replace(' ', '_') in entry.id
}
