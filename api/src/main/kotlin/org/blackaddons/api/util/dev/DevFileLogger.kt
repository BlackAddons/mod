package org.blackaddons.api.util.dev
import org.blackaddons.api.log.Log
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Append-only log at `<configDir>/dev/logs/<name>.log`, separate from `latest.log`. Shared per name (writes
 * are serialized); use the tagged [write] for shared files. Writes no-op unless [DevFlags.verboseLogging];
 * the first write per JVM adds a session marker.
 */
class DevFileLogger private constructor(private val name: String) {

    // Lazy: loggers are made in static initializers, which can run before the host binds Platform.
    val path: Path by lazy { DevFiles.subdir("logs").resolve("$name.log") }

    private val lock = Any()
    private var writer: BufferedWriter? = null

    /** Timestamped. No-op unless verbose logging is on. */
    fun write(line: String) {
        if (!DevFlags.verboseLogging) return
        synchronized(lock) {
            val w = ensureOpen() ?: return
            try {
                w.write("${LocalDateTime.now().format(TIMESTAMP)}  $line")
                w.newLine()
                w.flush()
            } catch (t: Throwable) {
                Log.main.error("Failed to write dev log {}", name, t)
                closeQuietly()
            }
        }
    }

    fun write(tag: String, line: String) = write("[$tag] $line")

    private fun ensureOpen(): BufferedWriter? {
        writer?.let { return it }
        return try {
            Files.createDirectories(path.parent)
            val w = Files.newBufferedWriter(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
            w.write("session start ${LocalDateTime.now()}")
            w.newLine()
            w.flush()
            writer = w
            w
        } catch (t: Throwable) {
            Log.main.error("Failed to open dev log at {}", path, t)
            null
        }
    }

    private fun closeQuietly() {
        try { writer?.close() } catch (_: Throwable) { }
        writer = null
    }

    companion object {
        private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        private val cache = ConcurrentHashMap<String, DevFileLogger>()

        private val VALID_NAME = Regex("^[a-zA-Z0-9_-]+$")

        /** Cached per name. Restricted to `[a-zA-Z0-9_-]+` so a name can't escape the logs directory. */
        fun named(name: String): DevFileLogger {
            require(VALID_NAME.matches(name)) {
                "DevFileLogger name must match [a-zA-Z0-9_-]+, got: '$name'"
            }
            return cache.computeIfAbsent(name) { DevFileLogger(it) }
        }
    }
}
