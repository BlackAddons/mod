package org.blackaddons.core.profile

import org.blackaddons.api.util.Uuids
import org.blackaddons.core.cosmetics.store.CosmeticOwners
import org.blackaddons.core.profile.PlayerNames.candidates
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Every name -> UUID seen this session, so we still know players after their tab entry is gone (Hypixel's Skyblock tab
 * only shows names). Bindings expire, because names change.
 */
object PlayerNames {

    /** 3-16 word chars. Hypixel panel slots (`!A-b`) fail it. */
    private val NAME = Regex("^\\w{3,16}$")

    private val byName = ConcurrentHashMap<String, Entry>()

    /** Real accounts only. [Uuids.isAccount] does the real filtering. */
    fun remember(name: String?, uuid: UUID?) {
        if (name == null || uuid == null) return
        if (!Uuids.isAccount(uuid) || !NAME.matches(name)) return
        if (byName.size >= MAX_ENTRIES) sweep()
        if (byName.size >= MAX_ENTRIES) return
        byName[name.lowercase()] = Entry(uuid, name, System.currentTimeMillis())
    }

    fun uuidFor(name: String): UUID? {
        val key = name.lowercase()
        val entry = byName[key] ?: return null
        if (entry.isExpired()) {
            byName.remove(key)
            return null
        }
        return entry.uuid
    }

    fun known(): Map<String, UUID> {
        sweep()
        return byName.mapValues { it.value.uuid }
    }

    /**
     * First-hand only: UUID plus the server's spelling. Narrower than [candidates], because the owner directory can be
     * late after a rename. That's fine for decoration, but not for picking whose profile to open.
     */
    fun sighting(name: String): Sighting? {
        val key = name.lowercase()
        val entry = byName[key] ?: return null
        if (entry.isExpired()) {
            byName.remove(key)
            return null
        }
        return Sighting(entry.uuid, entry.name)
    }

    data class Sighting(val uuid: UUID, val name: String)

    fun nameOf(uuid: UUID): String? = byName.values.firstOrNull { it.uuid == uuid && !it.isExpired() }?.name

    /** Owner directory with sightings on top. Sightings win, because they're newer. */
    fun candidates(): Map<String, UUID> = CosmeticOwners.known() + known()

    private fun sweep() {
        val now = System.currentTimeMillis()
        byName.values.removeIf { it.isExpired(now) }
    }

    private fun Entry.isExpired(now: Long = System.currentTimeMillis()) = now - seenAt > EXPIRY_MS

    // Keeps the original spelling. The key is lowercase.
    private class Entry(val uuid: UUID, val name: String, val seenAt: Long)

    /** Longer than a server hop, still short enough to catch renames. */
    private const val EXPIRY_MS = 60L * 60L * 1000L

    private const val MAX_ENTRIES = 2000
}
