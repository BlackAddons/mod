package org.blackaddons.core.screenshots

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.core.log.Log
import org.blackaddons.core.media.Thumbnails
import org.blackaddons.core.storage.GuardedFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** [takenAt] is the file mtime. */
internal data class Screenshot(val path: Path, val takenAt: Long) {
    val name: String get() = path.name
}

internal object ScreenshotLibrary {

    val folder: Path get() = Platform.gameDir.resolve("screenshots")

    private val favoritesFile by lazy { GuardedFile(Platform.configDir.resolve("screenshot-favorites.json")) }

    // Names, so they stay valid when the game dir moves.
    private val favorites: MutableSet<String> by lazy { ConcurrentHashMap.newKeySet<String>().apply { addAll(readFavorites()) } }

    /** Newest first. Disk I/O, so call it off the client thread. */
    fun list(): List<Screenshot> {
        if (!Files.isDirectory(folder)) return emptyList()
        return Files.list(folder).use { files ->
            files.filter { it.isRegularFile() && it.extension.lowercase() in EXTENSIONS }
                .map { Screenshot(it, it.getLastModifiedTime().toMillis()) }
                .toList()
        }.sortedByDescending { it.takenAt }
    }

    fun isFavorite(shot: Screenshot): Boolean = shot.name in favorites

    fun toggleFavorite(shot: Screenshot) {
        if (!favorites.remove(shot.name)) favorites += shot.name
        saveFavorites()
    }

    fun delete(shot: Screenshot): Boolean {
        val deleted = runCatching { Files.deleteIfExists(shot.path); true }
            .onFailure { Log.main.warn("Could not delete screenshot {}", shot.path, it) }
            .getOrDefault(false)
        if (!deleted) return false
        if (favorites.remove(shot.name)) saveFavorites()
        Thumbnails.forget(shot.path)
        return true
    }

    fun copyToClipboard(shot: Screenshot) {
        Platform.clipboard.copyImage(shot.path) { copied ->
            if (copied) Toasts.show(I18n.trOr("blackaddons.screenshots.copied", "Screenshot copied"), kind = ToastKind.SUCCESS)
            else Toasts.show(I18n.trOr("blackaddons.screenshots.copy_failed", "Could not copy the screenshot"), kind = ToastKind.ERROR)
        }
    }

    fun thumbnail(shot: Screenshot, onReady: (String) -> Unit = {}): String? =
        Thumbnails.request(shot.path, THUMB_W, THUMB_H, onReady)

    fun aspect(shot: Screenshot): Float? = Thumbnails.aspect(shot.path)

    private fun readFavorites(): Set<String> {
        val text = favoritesFile.read() ?: return emptySet()
        return runCatching { JsonParser.parseString(text).asJsonArray.map { it.asString }.toSet() }
            .getOrElse {
                favoritesFile.refuseWrites("not a list of names")
                emptySet()
            }
    }

    private fun saveFavorites() {
        favoritesFile.write(JsonArray().apply { favorites.sorted().forEach(::add) }.toString())
    }

    private val EXTENSIONS = setOf("png", "jpg", "jpeg")

    const val THUMB_W = 320
    const val THUMB_H = 180
}
