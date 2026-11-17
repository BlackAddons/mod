package org.blackaddons.core.theme

import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.contrastFailures
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.Portable
import org.blackaddons.core.log.Log
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shipped themes plus `<config>/themes/<id>.json`. Not a GuardedFile: a theme only gets written when edited, so there's
 * no empty state to overwrite with.
 */
internal object ThemeCatalog {

    const val KIND = "theme"

    private const val DIR = "themes"

    @Volatile
    private var user: List<ThemeDocument> = emptyList()

    val all: List<ThemeDocument> get() = BuiltInThemes.all + user

    fun byId(id: String): ThemeDocument? = all.firstOrNull { it.id == id }

    fun apply(document: ThemeDocument) {
        Theme.palette = document.spec.resolve()
    }

    fun load() {
        val dir = directory()
        if (!Files.isDirectory(dir)) {
            user = emptyList()
            return
        }
        user = Files.list(dir).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".json") }
                .map(::readFile)
                .toList()
                .filterNotNull()
        }
    }

    /** Refuses built-in ids, so editing Midnight saves a new theme. */
    fun save(document: ThemeDocument): Boolean {
        if (BuiltInThemes.all.any { it.id == document.id }) return false
        // Loading does the same check. Without it, the theme would save and then not show up in the list.
        if (document.spec.resolve().contrastFailures().isNotEmpty()) return false
        return runCatching {
            Disk.write(directory().resolve("${document.id}.json"), ThemeJson.write(document).toString())
            load()
        }.onFailure { Log.main.warn("Could not save theme '{}'", document.id, it) }.isSuccess
    }

    fun delete(id: String): Boolean {
        if (BuiltInThemes.all.any { it.id == id }) return false
        return runCatching {
            Files.deleteIfExists(directory().resolve("$id.json"))
            load()
        }.onFailure { Log.main.warn("Could not delete theme '{}'", id, it) }.isSuccess
    }

    fun export(document: ThemeDocument): String = Portable.encode(KIND, ThemeJson.write(document))

    fun freeId(name: String): String {
        val base = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').replace(Regex("-+"), "-")
            .ifBlank { "theme" }
        if (byId(base) == null) return base
        return generateSequence(2) { it + 1 }.first { byId("$base-$it") == null }.let { "$base-$it" }
    }

    private fun readFile(path: Path): ThemeDocument? {
        val id = path.fileName.toString().removeSuffix(".json")
        val text = runCatching { Disk.read(path) }.getOrNull() ?: return reject(id, "unreadable")
        val json = runCatching { JsonParser.parseString(text) }.getOrNull()?.takeIf { it.isJsonObject }
            ?: return reject(id, "not JSON")
        val document = ThemeJson.read(json.asJsonObject, id) ?: return reject(id, "no base colors")

        val failures = document.spec.resolve().contrastFailures()
        if (failures.isNotEmpty()) {
            return reject(id, "unreadable palette: ${failures.joinToString { "${it.foreground} on ${it.background}" }}")
        }
        return document
    }

    private fun reject(id: String, why: String): ThemeDocument? {
        Log.main.debug("Skipping theme '{}': {}", id, why)
        return null
    }

    private fun directory(): Path = Platform.configDir.resolve(DIR)
}
