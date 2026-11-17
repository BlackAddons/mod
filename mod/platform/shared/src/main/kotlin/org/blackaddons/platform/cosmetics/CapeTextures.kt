package org.blackaddons.platform.cosmetics

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.util.Hashes
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.api.util.io.FileFormat
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.log.Log
import org.blackaddons.platform.cosmetics.CapeTextures.sweep
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO

/** Stops decoding an unseen cape, resumes when it's rendered. */
internal const val IDLE_PAUSE_MS = 2_000L

/** Then frees its texture. A streamed cape can rebuild one. */
private const val IDLE_TEXTURE_MS = 30_000L

/** Then drops it entirely, so it doesn't get re-downloaded on every glimpse. */
private const val IDLE_EVICT_MS = 300_000L

/**
 * Downloads and caches capes, giving the render path an [Identifier] per player. The catalog's `capeUrl` carries a
 * `?v=` content hash.
 *
 * Animated capes stream like video: compressed source in memory, one background thread decoding just ahead into a short
 * queue, one texture. Memory is flat in frame count (decoding everything up front costs frames x w x h x 4, gigabytes
 * at high res). Playback is in [CapeEntry], pixels in [CapeImages].
 *
 * Downloads, decodes and allocations run off-thread. GL work and the cache maps are client-thread only. Idle entries
 * pause, release textures, then evict ([sweep], each client tick).
 */
object CapeTextures {
    /** LRU bound on distinct capes in view. Raise it if crowded lobbies show cape pop-in. */
    private const val MAX_ENTRIES = 64

    /** Mirrors the backend's cape size cap, which bounds animation length (memory is flat in frames). */
    private const val MAX_BYTES = 8 * 1024 * 1024

    /** A sanity bound on loop length, not memory. */
    private const val MAX_FRAMES = 600

    /** Back off after a failed URL. */
    private const val RETRY_AFTER_MS = 30_000L

    /** Decoder nap when every cape is fed or paused. */
    private const val DECODE_IDLE_SLEEP_MS = 15L

    // Client thread only.
    private val loaded = object : LinkedHashMap<String, CapeEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CapeEntry>): Boolean {
            if (size <= MAX_ENTRIES) return false
            drop(eldest.value)
            return true
        }
    }
    private val inFlight = HashSet<String>()
    private val failedUntil = HashMap<String, Long>()

    /** The decoder removes dropped ones itself. */
    private val streaming = CopyOnWriteArrayList<AnimatedCape>()

    @Volatile private var decoder: Thread? = null

    init {
        EventListener(ClientTickEvent.Post::class.java, EventPriority.NORMAL) { sweep() }.register()
    }

    /**
     * The texture for [capeUrl], or null while loading or failed. Client thread, every frame: calling it keeps the cape
     * alive. [rotated] is the copy for mirrored players.
     */
    fun identifierFor(capeUrl: String, rotated: Boolean = false): Identifier? {
        loaded[capeUrl]?.let { return it.identifier(rotated) }
        if (capeUrl in inFlight) return null
        failedUntil[capeUrl]?.let { if (System.currentTimeMillis() < it) return null }
        inFlight += capeUrl
        startLoad(capeUrl)
        return null
    }

    private fun startLoad(capeUrl: String) {
        Workers.start("cape-load") {
            val entry = runCatching { decode(capeUrl, download(resolve(capeUrl))) }.getOrElse { e ->
                Log.main.warn("Cape load failed for {}: {}", capeUrl, e.toString())
                null
            }
            Minecraft.getInstance().execute { finish(capeUrl, entry) }
        }
    }

    /** Load thread. GIFs are only probed (size and frame count from metadata), frames get decoded later. */
    private fun decode(capeUrl: String, bytes: ByteArray): Pending {
        val base = hash(capeUrl)
        if (FileFormat.detect(bytes) != FileFormat.GIF) {
            return Pending.Image(base, CapeImages.toCapeTexture(CapeImages.decodePng(bytes)))
        }

        val reader = CapeImages.gifReader()
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                reader.input = input
                val count = reader.getNumImages(true).coerceAtMost(MAX_FRAMES)
                if (count <= 0) throw IOException("Empty GIF")
                val (w, h) = CapeImages.screenSize(reader, reader.read(0))
                CapeImages.requireValidSize(w, h)
                // A single-frame GIF is just a still image with extra steps.
                if (count == 1) return Pending.Image(base, CapeImages.toCapeTexture(reader.read(0)))
                return Pending.Stream(base, bytes, count, w, h)
            }
        } finally {
            reader.dispose()
        }
    }

    /** Load-thread result. GL work and cache changes happen on the client thread. */
    private sealed interface Pending {
        class Image(val base: String, val image: NativeImage) : Pending
        class Stream(val base: String, val source: ByteArray, val frames: Int, val w: Int, val h: Int) : Pending
    }

    /** Client thread. */
    private fun finish(capeUrl: String, pending: Pending?) {
        inFlight -= capeUrl
        if (pending == null) {
            failedUntil[capeUrl] = System.currentTimeMillis() + RETRY_AFTER_MS
            return
        }
        try {
            loaded[capeUrl] = when (pending) {
                // GL is valid here, on the render thread.
                is Pending.Image -> StillCape(pending.base, pending.image)
                is Pending.Stream -> AnimatedCape(pending.base, pending.source, pending.frames, pending.w, pending.h)
                    .also { streaming += it; startDecoder() }
            }
        } catch (t: Throwable) {
            // Swallowing this would re-download every frame, so log and back off.
            Log.main.error("Cape GL upload failed for {}", capeUrl, t)
            failedUntil[capeUrl] = System.currentTimeMillis() + RETRY_AFTER_MS
        }
    }

    private fun startDecoder() {
        if (decoder != null) return
        decoder = Workers.start("cape-decode", ::decodeLoop)
    }

    /** Single decoder for all animated capes: decodes for any on-screen cape with queue room, sleeps otherwise. */
    private fun decodeLoop() {
        while (true) {
            var worked = false
            for (animation in streaming) {
                if (animation.dropped) {
                    streaming -= animation
                    animation.disposeDecoder()
                    continue
                }
                if (!animation.hungry()) continue
                worked = animation.decodeNext() || worked
            }
            if (!worked) Thread.sleep(DECODE_IDLE_SLEEP_MS)
        }
    }

    /** Client thread. Two-stage release of idle capes. */
    private fun sweep() {
        val now = System.currentTimeMillis()
        for ((url, entry) in loaded.entries.toList()) {
            val idle = now - entry.lastUsedMs
            when {
                idle > IDLE_EVICT_MS -> { loaded.remove(url); drop(entry) }
                // A still cape can't rebuild its texture, so it waits for eviction.
                idle > IDLE_TEXTURE_MS -> (entry as? AnimatedCape)?.releaseTextures()
            }
        }
    }

    /** Client thread. A streamed one frees its decoder state itself. */
    private fun drop(entry: CapeEntry) {
        entry.releaseTextures()
        (entry as? AnimatedCape)?.dropped = true
    }

    /** Root-relative catalog URLs get the prefix. */
    private fun resolve(capeUrl: String): String =
        if (capeUrl.startsWith("http://") || capeUrl.startsWith("https://")) capeUrl
        else Backend.BASE_URL + (if (capeUrl.startsWith("/")) capeUrl else "/$capeUrl")

    /** Throws so the caller backs off. */
    private fun download(url: String): ByteArray =
        Http.bytes(url, maxBytes = MAX_BYTES) ?: throw IOException("cape download failed: $url")

    private fun hash(url: String): String = Hashes.hex(Hashes.sha256(url.toByteArray())).take(16)
}
