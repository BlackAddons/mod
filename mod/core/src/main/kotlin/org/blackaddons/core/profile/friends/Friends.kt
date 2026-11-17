package org.blackaddons.core.profile.friends

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.log.Log
import java.time.Instant

/** The player's Hypixel friend list as last collected, persisted. There's no API, so it's only collected on demand. */
internal object Friends {

    private const val FILE = "friends.json"

    private var names = emptyList<String>()
    private var collectedAt: Instant? = null
    private var loaded = false

    @Synchronized
    fun names(): List<String> {
        if (!loaded) { load(); loaded = true }
        return names
    }

    @Synchronized
    fun collectedAt(): Instant? {
        if (!loaded) { load(); loaded = true }
        return collectedAt
    }

    /** Merges [seen] and returns how many are new. Never removes, because a page is a partial view. */
    @Synchronized
    fun remember(seen: Collection<String>): Int {
        if (seen.isEmpty()) return 0
        if (!loaded) { load(); loaded = true }
        val merged = (names + seen).distinct().sorted()
        if (merged == names) return 0 // same page again must not rewrite the file
        val added = merged.size - names.size
        names = merged
        collectedAt = Instant.now()
        save()
        return added
    }

    @Synchronized
    fun forget(name: String) {
        if (!loaded) { load(); loaded = true }
        val kept = names.filterNot { it.equalsIgnoreCase(name) }
        if (kept == names) return
        names = kept
        save()
    }

    private fun load() {
        names = emptyList()
        collectedAt = null
        val path = Platform.configDir.resolve(FILE)
        runCatching {
            val root = JsonParser.parseString(Disk.read(path) ?: return).asJsonObject
            names = root.getAsJsonArray("names").map { it.asString }.distinct().sorted()
            collectedAt = root.get("collectedAt")?.takeIf { !it.isJsonNull }?.let { Instant.parse(it.asString) }
        }.onFailure { Log.main.warn("Failed to read the friend list", it) }
    }

    private fun save() {
        val path = Platform.configDir.resolve(FILE)
        runCatching {
            val root = JsonObject().apply {
                add("names", Gsons.pretty.toJsonTree(names))
                addProperty("collectedAt", collectedAt?.toString())
            }
            Disk.write(path, Gsons.pretty.toJson(root))
        }.onFailure { Log.main.warn("Failed to persist the friend list", it) }
    }
}
