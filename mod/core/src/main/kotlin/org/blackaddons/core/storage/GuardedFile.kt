package org.blackaddons.core.storage

import org.blackaddons.api.util.io.Disk
import org.blackaddons.core.log.Log
import java.nio.file.Files
import java.nio.file.Path

/**
 * A file that refuses writes after a failed read, so a store that keeps going with empty state can't overwrite the
 * player's only copy. Missing or empty files aren't failures.
 */
internal class GuardedFile(
    val path: Path,
    private val maxBytes: Long = MAX_BYTES,
) {

    @Volatile
    var writable: Boolean = true
        private set

    /** Null when there's nothing yet. A successful read clears the latch. */
    fun read(): String? {
        writable = true
        if (!Files.exists(path)) return null

        return runCatching {
            val size = Files.size(path)
            if (size > maxBytes) {
                refuseWrites("$size bytes exceeds the $maxBytes byte cap")
                return null
            }
            Files.readString(path).takeIf(String::isNotBlank)
        }.onFailure {
            Log.main.error("Failed to read {}", path, it)
            writable = false
        }.getOrNull()
    }

    /** For bytes that read fine but mean nothing (parse error, wrong shape). */
    fun refuseWrites(reason: String) {
        Log.main.error("Refusing to overwrite {} - {}", path, reason)
        writable = false
    }

    fun write(text: String): Boolean {
        if (!writable) {
            Log.main.warn("Not saving {} because the existing file could not be read", path)
            return false
        }
        return runCatching { Disk.write(path, text) }
            .onFailure { Log.main.error("Failed to save {}", path, it) }
            .isSuccess
    }

    private companion object {
        /** Past this, it's not our file. */
        const val MAX_BYTES = 10L * 1024L * 1024L
    }
}
