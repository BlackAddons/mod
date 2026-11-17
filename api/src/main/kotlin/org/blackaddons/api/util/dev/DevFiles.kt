package org.blackaddons.api.util.dev
import org.blackaddons.api.Platform
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Paths under `<configDir>/dev`, the single home for dev-only files. */
object DevFiles {

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** Not created. */
    fun subdir(name: String): Path = Platform.configDir.resolve("dev").resolve(name)

    /**
     * Writes `<configDir>/dev/<dirName>/<prefix>-<timestamp>.<ext>`, creating directories; returns the path.
     */
    fun writeTimestamped(dirName: String, prefix: String, ext: String, content: String): Path {
        val dir = subdir(dirName)
        Files.createDirectories(dir)
        val file = dir.resolve("$prefix-${LocalDateTime.now().format(STAMP)}.$ext")
        Files.writeString(file, content)
        return file
    }
}
