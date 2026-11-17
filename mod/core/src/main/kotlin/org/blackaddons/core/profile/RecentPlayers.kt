package org.blackaddons.core.profile

import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.log.Log
import org.blackaddons.core.profile.RecentPlayers.MAX

/**
 * Profile Viewer history, newest first, persisted. The PV draws a few as heads. `/blackaddons pv` completes from all of
 * them, so [MAX] is larger. [skin] saves another Mojang lookup.
 */
internal object RecentPlayers {

    data class Entry(val uuid: String, val name: String, val skin: String?)

    private const val MAX = 50
    private const val FILE = "pv_recent.json"

    // Replaced whole, so reads each frame skip the lock.
    @Volatile private var entries: List<Entry>? = null

    fun list(): List<Entry> = entries ?: synchronized(this) { entries ?: load().also { entries = it } }

    @Synchronized
    fun record(uuid: String, name: String, skin: String?) {
        val next = listOf(Entry(uuid, name, skin)) + list().filterNot { it.uuid.equalsIgnoreCase(uuid) }
        entries = next.take(MAX).also(::save)
    }

    private fun load(): List<Entry> {
        val path = Platform.configDir.resolve(FILE)
        val loaded = ArrayList<Entry>()
        runCatching {
            JsonParser.parseString(Disk.read(path) ?: return loaded).asJsonArray.forEach {
                val o = it.asJsonObject
                val uuid = o.get("uuid")?.asString ?: return@forEach
                val name = o.get("name")?.asString ?: return@forEach
                loaded.add(Entry(uuid, name, o.get("skin")?.takeIf { s -> !s.isJsonNull }?.asString))
            }
        }.onFailure { Log.main.warn("Failed to read recent players", it) }
        return loaded
    }

    private fun save(list: List<Entry>) {
        val path = Platform.configDir.resolve(FILE)
        runCatching {
            Disk.write(path, Gsons.pretty.toJson(list))
        }.onFailure { Log.main.warn("Failed to persist recent players", it) }
    }
}
