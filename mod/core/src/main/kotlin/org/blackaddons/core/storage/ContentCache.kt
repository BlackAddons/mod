package org.blackaddons.core.storage

import org.blackaddons.api.Platform
import org.blackaddons.api.util.Hashes
import org.blackaddons.api.util.io.Disk
import org.blackaddons.core.log.Log
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * Disk cache keyed by sha256 (cosmetic assets, chat pictures). Never stale, only size-capped. Downloads get verified,
 * so bad responses can't poison it. One instance and budget per content kind, so chat can't evict cosmetic models.
 * Blocking.
 */
internal class ContentCache(private val dirName: String, private val maxBytes: Long) {

    fun bytes(sha256: String, url: String): ByteArray? {
        cached(sha256)?.let { file ->
            runCatching { return Files.readAllBytes(file) }
                .onFailure { Log.main.warn("Reading cached {} failed: {}", sha256, it.toString()) }
        }
        val data = download(sha256, url) ?: return null
        store(sha256, data)
        return data
    }

    fun path(sha256: String, url: String): Path? =
        cached(sha256) ?: download(sha256, url)?.let { store(sha256, it) }

    fun path(sha256: String): Path? = cached(sha256)

    fun put(data: ByteArray): String {
        val sha256 = digest(data)
        if (cached(sha256) == null) checkNotNull(store(sha256, data)) { "Could not store cached content" }
        return sha256
    }

    fun remove(sha256: String): Boolean {
        if (!validKey(sha256)) return false
        val file = runCatching { dir().resolve(sha256) }.getOrNull() ?: return false
        return runCatching { Files.deleteIfExists(file) }
            .onFailure { Log.main.warn("Removing cached {} failed: {}", sha256, it.toString()) }
            .getOrDefault(false)
    }

    private fun cached(sha256: String): Path? {
        if (!validKey(sha256)) return null
        val file = runCatching { dir().resolve(sha256) }.getOrNull() ?: return null
        if (!Files.exists(file)) return null
        // mtime is the access record prune reads.
        runCatching { Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis())) }
        return file
    }

    private fun validKey(sha256: String): Boolean = Hashes.isSha256Hex(sha256.lowercase())

    private fun download(sha256: String, url: String): ByteArray? {
        val data = Platform.http.bytesBlocking(url) ?: return null
        if (verify(data, sha256)) return data
        Log.main.warn("{} failed its integrity check - discarding", sha256)
        return null
    }

    private fun store(sha256: String, data: ByteArray): Path? = runCatching {
        val file = dir().resolve(sha256)
        Disk.write(file, data)
        prune()
        file
    }.onFailure { Log.main.warn("Caching {} failed: {}", sha256, it.toString()) }.getOrNull()

    private fun dir(): Path = Platform.configDir.resolve(dirName).also { Files.createDirectories(it) }

    private fun verify(data: ByteArray, sha256: String): Boolean {
        return digest(data).equals(sha256, ignoreCase = true)
    }

    private fun digest(data: ByteArray): String = Hashes.hex(Hashes.sha256(data))

    // Best-effort LRU by mtime. Runs on (rare) writes.
    private fun prune() {
        val entries = ArrayList<Entry>()
        var total = 0L
        Files.newDirectoryStream(dir()).use { stream ->
            for (path in stream) {
                if (!Files.isRegularFile(path) || path.fileName.toString().endsWith(".tmp")) continue
                val entry = Entry(
                    path,
                    runCatching { Files.size(path) }.getOrDefault(0L),
                    runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L),
                )
                entries += entry
                total += entry.size
            }
        }
        if (total <= maxBytes) return

        entries.sortBy { it.modified }
        for (entry in entries) {
            if (total <= maxBytes) break
            if (runCatching { Files.deleteIfExists(entry.path) }.getOrDefault(false)) total -= entry.size
        }
    }

    private class Entry(val path: Path, val size: Long, val modified: Long)
}
