package org.blackaddons.core.cosmetics.mesh
import org.blackaddons.api.Platform
import org.blackaddons.api.util.Hashes
import org.blackaddons.api.util.io.Disk
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.log.Log
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * Content-addressed disk cache for cosmetic assets. The hash is the identity, so there's no invalidation, only an LRU
 * cap. Atomic writes and a hash check keep bad downloads out. Public for the platform's 3D pipeline.
 */
object CosmeticAssets {
    private const val DIR = "cosmetic-assets"
    private const val MAX_BYTES = 256L * 1024L * 1024L


    /** From disk, or fetched and verified. Null on failure or hash mismatch. Blocking. */
    fun bytes(sha256: String, url: String): ByteArray? {
        val file = runCatching { dir().resolve(sha256) }.getOrNull() ?: return null
        if (Files.exists(file)) {
            runCatching {
                val cached = Files.readAllBytes(file)
                touch(file)
                return cached
            }.onFailure { Log.main.warn("Reading cached asset {} failed: {}", sha256, it.toString()) }
        }
        val data = Platform.http.bytesBlocking(resolve(url)) ?: return null
        if (!verify(data, sha256)) {
            Log.main.warn("Cosmetic asset {} failed its integrity check - discarding", sha256)
            return null
        }
        runCatching {
            Disk.write(file, data)
            prune()
        }.onFailure { Log.main.warn("Caching asset {} failed: {}", sha256, it.toString()) }
        return data
    }

    private fun dir(): Path = Platform.configDir.resolve(DIR).also { Files.createDirectories(it) }

    /** Root-relative manifest URLs get the backend prefix, like `capeUrl`. */
    private fun resolve(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url
        else Backend.BASE_URL + (if (url.startsWith("/")) url else "/$url")

    private fun verify(data: ByteArray, sha256: String): Boolean =
        Hashes.hex(Hashes.sha256(data)).equals(sha256, ignoreCase = true)

    private fun touch(file: Path) {
        runCatching { Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis())) }
    }

    // Best-effort LRU by mtime. Runs on writes.
    private fun prune() {
        val entries = ArrayList<Triple<Path, Long, Long>>() // path, size, mtime
        var total = 0L
        Files.newDirectoryStream(dir()).use { stream ->
            for (p in stream) {
                if (!Files.isRegularFile(p) || p.fileName.toString().endsWith(".tmp")) continue
                val size = runCatching { Files.size(p) }.getOrDefault(0L)
                val mtime = runCatching { Files.getLastModifiedTime(p).toMillis() }.getOrDefault(0L)
                entries.add(Triple(p, size, mtime))
                total += size
            }
        }
        if (total <= MAX_BYTES) return
        entries.sortBy { it.third }
        for ((path, size, _) in entries) {
            if (total <= MAX_BYTES) break
            if (runCatching { Files.deleteIfExists(path) }.getOrDefault(false)) total -= size
        }
    }
}
