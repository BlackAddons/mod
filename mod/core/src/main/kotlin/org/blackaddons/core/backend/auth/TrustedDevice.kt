package org.blackaddons.core.backend.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.io.Disk
import org.blackaddons.core.backend.Timestamps
import org.blackaddons.core.log.Log
import java.time.Instant
import java.util.UUID

/**
 * 30-day device token that replaces the 2FA code, keyed by Minecraft account (for the account switcher). Read from disk
 * every time: reads are rare, off-thread and never stale. It's a credential, so it's opt-in.
 */
internal object TrustedDevice {

    /** Not in the config, because players share configs. */
    private const val FILE = "device.json"

    private const val FALLBACK_TTL_MS = 30L * 24 * 60 * 60 * 1000

    private val lock = Any()

    fun tokenFor(uuid: UUID): String? = synchronized(lock) {
        val entries = load()
        val entry = entries[uuid] ?: return null
        if (entry.expiresAtMs > System.currentTimeMillis()) return entry.token
        // To the backend, expired is the same as absent.
        entries.remove(uuid)
        save(entries)
        return null
    }

    fun remember(uuid: UUID, token: String, expiresAt: String?) = synchronized(lock) {
        val entries = load()
        entries[uuid] = Entry(token, expiry(expiresAt))
        save(entries)
    }

    /**
     * Called when a handshake with a token came back with one factor. That's the only sign the device isn't trusted
     * anymore.
     */
    fun forget(uuid: UUID) = synchronized(lock) {
        val entries = load()
        if (entries.remove(uuid) != null) save(entries)
    }

    private fun load(): MutableMap<UUID, Entry> {
        val entries = HashMap<UUID, Entry>()
        runCatching {
            val text = Disk.read(path()) ?: return@runCatching
            JsonParser.parseString(text).asJsonObject.entrySet().forEach { (key, value) ->
                // Per entry, so one bad entry doesn't wipe the others on the next write.
                runCatching {
                    val obj = value as? JsonObject ?: return@runCatching
                    val token = obj.get("token")?.takeIf { !it.isJsonNull }?.asString ?: return@runCatching
                    val expiresAt = obj.get("expiresAt")?.takeIf { !it.isJsonNull }?.asString
                    val uuid = Uuids.parse(key) ?: return@runCatching
                    entries[uuid] = Entry(token, expiry(expiresAt))
                }
            }
        }.onFailure { Log.main.warn("Couldn't read remembered devices", it) }
        return entries
    }

    private fun save(entries: Map<UUID, Entry>) {
        val root = JsonObject()
        entries.forEach { (uuid, entry) ->
            root.add(
                uuid.toString(),
                JsonObject().apply {
                    addProperty("token", entry.token)
                    addProperty("expiresAt", Instant.ofEpochMilli(entry.expiresAtMs).toString())
                },
            )
        }
        // A failed write only costs one code on the next launch.
        runCatching { Disk.write(path(), root.toString()) }
            .onFailure { Log.main.warn("Couldn't remember this device", it) }
    }

    private fun path() = Platform.configDir.resolve(FILE)

    // A token without a date gets the default window. The server refuses it if it's expired.
    private fun expiry(raw: String?): Long =
        Timestamps.parse(raw) ?: (System.currentTimeMillis() + FALLBACK_TTL_MS)

    private data class Entry(val token: String, val expiresAtMs: Long)
}
