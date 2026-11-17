package org.blackaddons.extras.features.impl.extras.storage

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.util.io.int
import org.blackaddons.api.util.io.str
import org.blackaddons.extras.ExtrasAddon

/**
 * Ender chest pages and backpacks captured when opened, so u can browse them offline. Keyed
 * `<accountId>/<profile>/<containerKey>` like NEU, so nothing leaks across profiles. Without an account or profile
 * every call is a no-op.
 */
internal object StorageCache {

    private const val PREFIX = "enderchest/"
    private const val MANIFEST = "@manifest"
    private const val NAMES = "@names"

    private val storage by lazy { Platform.storage.forAddon(ExtrasAddon.id) }

    /**
     * Parsed snapshot of the current scope: the browser reads all of it every frame, and parsing dozens of pages from
     * JSON at 60 fps tanked FPS. Client thread only.
     */
    private var loadedScope: String? = null
    private var containers: Map<String, CapturedContainer> = emptyMap()
    private var manifestRefs: List<StorageRef> = emptyList()
    private var customNames: Map<String, String> = emptyMap()
    private var entryRefs: List<StorageRef> = emptyList()
    private var stale = true

    /** Bumped on change, so views ([StorageTileList]) only rebuild when it moves. */
    var version = 0
        private set

    /** Reloads when the profile changed or a write invalidated it. */
    private fun sync() {
        val scope = scope()
        if (!stale && scope == loadedScope) return

        loadedScope = scope
        stale = false
        version++

        if (scope == null) {
            containers = emptyMap()
            manifestRefs = emptyList()
            customNames = emptyMap()
            entryRefs = emptyList()
            return
        }

        containers = storage.keys()
            // Skip meta entries (`@manifest`, `@names`, ...).
            .filter { it.startsWith(scope) && !it.removePrefix(scope).startsWith("@") }
            .mapNotNull { read(it) }
            .associateBy { it.key }
        manifestRefs = readManifest(scope).sortedWith(compareBy({ it.type.ordinal }, { it.page }))
        customNames = readNames(scope)

        val byKey = LinkedHashMap<String, StorageRef>()
        for (ref in manifestRefs) if (ref.openable) byKey[ref.key] = ref
        for (c in containers.values) byKey.putIfAbsent(c.key, StorageRef(c.type, c.page, c.title))
        entryRefs = byKey.values.sortedWith(compareBy({ it.type.ordinal }, { it.page }))
    }

    private fun invalidate() {
        stale = true
    }

    /** Key prefix for account + profile, or null off Skyblock. A hidden profile name falls back to account-only. */
    private fun scope(): String? {
        val account = Platform.skyblock.accountId ?: return null
        val profile = Platform.skyblock.profile ?: "default"
        return "$PREFIX$account/$profile/"
    }

    fun put(container: CapturedContainer) {
        val scope = scope() ?: return
        storage.putJson(scope + container.key, container.toJson())
        invalidate()
    }

    fun get(key: String): CapturedContainer? {
        sync()
        return containers[key]
    }

    /** Ender chests, then backpacks, by page. */
    fun all(): List<CapturedContainer> {
        sync()
        return containers.values.sortedWith(compareBy({ it.type.ordinal }, { it.page }))
    }

    fun putManifest(refs: List<StorageRef>) {
        val scope = scope() ?: return
        val arr = JsonArray()
        for (ref in refs) arr.add(JsonObject().apply {
            addProperty("type", ref.type.name)
            addProperty("page", ref.page)
            addProperty("label", ref.label)
            ref.icon?.let { addProperty("icon", it) }
            addProperty("state", ref.state.name)
            addProperty("slot", ref.slot)
        })
        storage.putJson(scope + MANIFEST, arr)
        invalidate()
    }

    private fun readManifest(scope: String): List<StorageRef> {
        val arr = storage.getJson(scope + MANIFEST) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            runCatching {
                StorageRef(
                    ContainerType.valueOf(o.str("type").orEmpty()),
                    o.int("page"),
                    o.str("label").orEmpty(),
                    o.str("icon"),
                    o.str("state")?.let(SlotState::valueOf) ?: SlotState.OPEN,
                    o.int("slot", -1),
                )
            }.getOrNull()
        }
    }

    /** Openable manifest entries plus cached pages that are missing from it (like an ender chest opened directly). */
    fun entries(): List<StorageRef> {
        sync()
        return entryRefs
    }

    /** Full overview manifest including empty and locked slots, with icons, for the selector. */
    fun selectorRefs(): List<StorageRef> {
        sync()
        return manifestRefs
    }

    fun names(): Map<String, String> {
        sync()
        return customNames
    }

    private fun readNames(scope: String): Map<String, String> {
        val obj = storage.getJson(scope + NAMES) as? JsonObject ?: return emptyMap()
        // Skip malformed entries instead of crashing the render.
        return obj.keySet().mapNotNull { k -> obj.str(k)?.let { k to it } }.toMap()
    }

    /** Null [name] clears it. */
    fun setName(key: String, name: String?) {
        val scope = scope() ?: return
        val obj = storage.getJson(scope + NAMES) as? JsonObject ?: JsonObject()
        if (name.isNullOrBlank()) obj.remove(key) else obj.addProperty(key, name)
        storage.putJson(scope + NAMES, obj)
        invalidate()
    }

    fun clearProfile() {
        val scope = scope() ?: return
        storage.keys().filter { it.startsWith(scope) }.forEach(storage::remove)
        invalidate()
    }

    private fun read(fullKey: String): CapturedContainer? =
        (storage.getJson(fullKey) as? JsonObject)?.let { runCatching { CapturedContainer.fromJson(it) }.getOrNull() }
}
