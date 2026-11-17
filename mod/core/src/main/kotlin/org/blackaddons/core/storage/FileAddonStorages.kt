package org.blackaddons.core.storage

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.blackaddons.api.Platform
import org.blackaddons.api.host.AddonStorage
import org.blackaddons.api.host.AddonStorages
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.log.Log
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * One JSON object per addon at `configDir/addons/<addonId>.json`, cached per id. Ids are limited to `[a-zA-Z0-9_-]+` to
 * block path traversal.
 */
internal object FileAddonStorages : AddonStorages {

    private val cache = ConcurrentHashMap<String, AddonStorage>()
    private val VALID_ID = Regex("^[a-zA-Z0-9_-]+$")

    override fun forAddon(addonId: String): AddonStorage {
        require(VALID_ID.matches(addonId)) {
            "addonId must match [a-zA-Z0-9_-]+, got: '$addonId'"
        }
        return cache.computeIfAbsent(addonId) {
            FileAddonStorage(Platform.configDir.resolve("addons").resolve("$it.json"))
        }
    }
}

private class FileAddonStorage(path: Path) : AddonStorage {

    private val logger = Log.area("storage")
    // Only held briefly, because the render thread reads through it.
    private val lock = Any()
    // So an older snapshot never lands after a newer one.
    private val saveLock = Any()
    private val file = GuardedFile(path)

    private var loaded = false
    private val data: JsonObject = JsonObject()

    private inline fun <T> locked(block: () -> T): T = synchronized(lock) {
        if (!loaded) load()
        block()
    }

    private fun load() {
        loaded = true
        val text = file.read() ?: return
        try {
            val parsed = JsonParser.parseString(text)
            if (parsed.isJsonObject) {
                parsed.asJsonObject.entrySet().forEach { (k, v) -> data.add(k, v) }
            } else {
                file.refuseWrites("it is not a JSON object")
            }
        } catch (t: Throwable) {
            logger.error("Failed to load addon storage at {}", file.path, t)
            file.refuseWrites("it could not be parsed")
        }
    }

    private fun primitive(key: String): JsonPrimitive? = locked { data.get(key) as? JsonPrimitive }

    override fun has(key: String): Boolean = locked { data.has(key) }

    override fun getString(key: String, default: String?): String? =
        primitive(key)?.takeIf { it.isString }?.asString ?: default

    override fun getInt(key: String, default: Int): Int =
        primitive(key)?.takeIf { it.isNumber }?.asInt ?: default

    override fun getLong(key: String, default: Long): Long =
        primitive(key)?.takeIf { it.isNumber }?.asLong ?: default

    override fun getDouble(key: String, default: Double): Double =
        primitive(key)?.takeIf { it.isNumber }?.asDouble ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean =
        primitive(key)?.takeIf { it.isBoolean }?.asBoolean ?: default

    // Copies both ways, so callers can't change our state or race the save.
    override fun getJson(key: String): JsonElement? = locked { data.get(key)?.deepCopy() }

    override fun putString(key: String, value: String) = write(key, JsonPrimitive(value))
    override fun putInt(key: String, value: Int) = write(key, JsonPrimitive(value))
    override fun putLong(key: String, value: Long) = write(key, JsonPrimitive(value))
    override fun putDouble(key: String, value: Double) = write(key, JsonPrimitive(value))
    override fun putBoolean(key: String, value: Boolean) = write(key, JsonPrimitive(value))
    override fun putJson(key: String, value: JsonElement) = write(key, value.deepCopy())

    override fun remove(key: String) {
        locked { data.remove(key) }
        save()
    }

    override fun clear() {
        locked { data.keySet().toList().forEach(data::remove) }
        save()
    }

    override fun keys(): Set<String> = locked { data.keySet().toSet() }

    private fun write(key: String, value: JsonElement) {
        locked { data.add(key, value) }
        save()
    }

    // A store that failed to read saves nothing over the file.
    override fun save() {
        synchronized(saveLock) {
            val text = locked { Gsons.pretty.toJson(data) }
            file.write(text)
        }
    }
}
