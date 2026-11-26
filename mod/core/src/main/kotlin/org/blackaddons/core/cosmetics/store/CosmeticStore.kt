package org.blackaddons.core.cosmetics.store

import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.cosmetics.Cosmetic
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.api.util.io.Disk
import org.blackaddons.core.backend.BackendNotifier
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.cosmetics.PlayerCosmetics
import org.blackaddons.core.cosmetics.store.CosmeticStore.REFRESH_MINUTES
import org.blackaddons.core.log.Log
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cosmetics per UUID for rendering. A [lookup] miss queues the UUID, and a short debounce batches a lobby into one
 * resolve. [lookup] is lock-free. Mirrored to disk, so names show right away on the next launch. A change of
 * [onCatalogVersion] resolves everyone seen again.
 */
internal object CosmeticStore {
    private const val CACHE_FILE = "cosmetics-cache.json"

    private const val MAX_CACHE_BYTES = 10L * 1024L * 1024L

    /** `Cosmetics3d:MaxResolveBatch`. */
    private const val MAX_BATCH = 100

    /** Catches a lobby and still feels instant. */
    private const val DEBOUNCE_MS = 100L

    private const val RETRY_MS = 5_000L

    /** When no `Retry-After` was given. Same as the backend default. */
    private const val THROTTLED_SECONDS = 60L

    /** Polling when there is no socket. Resolve is cheap (Redis, 100/min), but without push changes come minutes late. */
    private const val REFRESH_MINUTES = 5L

    // Replaced whole for lock-free reads.
    @Volatile
    private var byUuid: Map<UUID, Cosmetic> = emptyMap()

    @Volatile
    private var source: CosmeticsSource? = null
    @Volatile
    private var diskLoaded = false

    // The first value is a baseline, not a change.
    @Volatile
    private var catalogVersion: String? = null

    /**
     * Answered UUIDs, also the ones without a cosmetic, so they don't get queued every frame. Cleared on version
     * change.
     */

    private val resolved: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    private val pending: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    private val flushArmed = AtomicBoolean(false)

    // The batch in flight is in neither set, so changes that name it would be lost. [batchLock] guards it with the drain.
    private val batchLock = Any()
    private var inFlight: Set<UUID> = emptySet()
    private val executor = Workers.scheduled("cosmetics")

    fun setSource(source: CosmeticsSource) {
        this.source = source
    }

    fun warmUp() = ensureDiskLoaded()

    /**
     * Polling half of Live Updates. Skips work while the gateway is up, otherwise changes show within
     * [REFRESH_MINUTES].
     */
    fun startRefreshing() {
        executor.scheduleWithFixedDelay(
            { runCatching { refreshUnlessPushed() }.onFailure { Log.main.warn("Cosmetics refresh failed: {}", it.toString()) } },
            REFRESH_MINUTES, REFRESH_MINUTES, TimeUnit.MINUTES,
        )
    }

    fun lookup(uuid: UUID?): Cosmetic? {
        if (uuid == null) return null
        ensureDiskLoaded()
        val hit = byUuid[uuid]
        // Only real accounts, because dungeons spawn endless fake players (see [Uuids.isAccount]).
        if (source != null && Uuids.isAccount(uuid) && uuid !in resolved && pending.add(uuid)) {
            scheduleFlush(DEBOUNCE_MS)
        }
        return hit
    }

    /**
     * `cosmeticsVersion` from `READY`/`HEARTBEAT_ACK`. Only compared for inequality, because it's a token and restarts
     * reset it to "0". A change resolves everyone seen again.
     */
    fun onCatalogVersion(version: String) {
        if (version == catalogVersion) return
        val first = catalogVersion == null
        catalogVersion = version
        if (first) return
        requeueSeen()
    }

    /**
     * [players] whose answers to drop, or null if the catalog moved. [version] gets recorded, so the next heartbeat
     * doesn't resolve the lobby again. Players we didn't see are skipped.
     */
    fun onCosmeticsChanged(version: String?, players: List<UUID>?) {
        version?.let { catalogVersion = it }
        if (players == null) {
            requeueSeen()
            return
        }
        val seen = synchronized(batchLock) {
            players.filter { it in resolved || it in inFlight }.also { pending.addAll(it) }
        }
        if (seen.isEmpty()) return
        resolved.removeAll(seen.toSet())
        scheduleFlush(DEBOUNCE_MS)
    }

    private fun refreshUnlessPushed() {
        if (Gateway.connected) return
        requeueSeen()
    }

    private fun requeueSeen() {
        val known = synchronized(batchLock) { HashSet(resolved).apply { addAll(inFlight) } }
        if (known.isEmpty()) return
        resolved.clear()
        pending.addAll(known)
        scheduleFlush(DEBOUNCE_MS)
    }

    private fun ensureDiskLoaded() {
        if (diskLoaded) return
        diskLoaded = true
        runCatching { loadCache() }.onFailure { Log.main.warn("Cosmetics cache load failed: {}", it.toString()) }
    }

    private fun scheduleFlush(delayMs: Long) {
        if (source == null) return
        if (flushArmed.compareAndSet(false, true)) executor.schedule(::runFlush, delayMs, TimeUnit.MILLISECONDS)
    }

    /** The armed flag covers the whole request. Clearing it at the start let a busy lobby send a request every 100 ms. */
    private fun runFlush() {
        val next = runCatching { flush() }.getOrElse {
            Log.main.warn("Cosmetics resolve failed: {}", it.toString())
            RETRY_MS
        }
        flushArmed.set(false)
        next?.let(::scheduleFlush)
    }

    private fun flush(): Long? {
        val src = source ?: return null
        val batch = synchronized(batchLock) { drain().also { inFlight = it.toSet() } }
        if (batch.isEmpty()) return null
        try {
            return resolve(src, batch)
        } finally {
            synchronized(batchLock) { inFlight = emptySet() }
        }
    }

    private fun resolve(src: CosmeticsSource, batch: List<UUID>): Long? {
        val root = when (val answer = src.resolve(batch)) {
            is CosmeticsAnswer.Manifest -> answer.root

            // Throttling isn't an outage. Wait exactly as told, silently.
            is CosmeticsAnswer.Later -> {
                pending.addAll(batch)
                return (answer.retryAfterSeconds?.toLong()?.coerceAtLeast(1) ?: THROTTLED_SECONDS) * 1000L
            }

            is CosmeticsAnswer.Failed -> {
                pending.addAll(batch)
                notifyLoadFailed()
                return RETRY_MS
            }
        }
        BackendNotifier.onReachable() // re-arm the outage notice

        val results = CosmeticJson.fromResolve(root)
        // Every requested UUID gets a fresh answer, also "none".
        val next = HashMap(byUuid)
        for (uuid in batch) next.remove(uuid)
        next.putAll(results)
        val changed = next != byUuid
        byUuid = next
        resolved.addAll(batch)

        // The real value arrived, so our local copy of our own cosmetic can go.
        if (Platform.player.uuid in batch) PlayerCosmetics.clearOwn()

        if (changed) runCatching { saveCache() }.onFailure { Log.main.warn("Cosmetics cache save failed: {}", it.toString()) }
        return DEBOUNCE_MS.takeIf { pending.isNotEmpty() } // more than one batch queued
    }

    private fun drain(): List<UUID> {
        val out = ArrayList<UUID>(minOf(MAX_BATCH, pending.size))
        val it = pending.iterator()
        while (it.hasNext() && out.size < MAX_BATCH) {
            out.add(it.next())
            it.remove()
        }
        return out
    }

    /** So a stale catalog doesn't look like "no cosmetics". Once per outage. */
    private fun notifyLoadFailed() = BackendNotifier.notifyUnreachable {
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr("blackaddons.cosmetics.load_failed"), Brand.FAIL)
        })
    }

    private fun cachePath() = Platform.configDir.resolve(CACHE_FILE)

    private fun loadCache() {
        val path = cachePath()
        if (!Files.exists(path)) return
        val size = Files.size(path)
        if (size > MAX_CACHE_BYTES) {
            Log.main.warn("Cosmetics cache {} is {} bytes (> {}), ignoring it", path, size, MAX_CACHE_BYTES)
            return
        }
        val json = JsonParser.parseString(Disk.read(path) ?: return).asJsonObject
        // Not marked as resolved, so each one refreshes when first seen.
        byUuid = CosmeticJson.fromCache(json)
    }

    private fun saveCache() {
        Disk.write(cachePath(), CosmeticJson.toCache(byUuid).toString())
    }
}
