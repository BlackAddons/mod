package org.blackaddons.core.font

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.FileFormat
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.core.backend.Http
import org.blackaddons.core.storage.ContentCache
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

internal data class FontCatalogEntry(
    val id: String,
    val family: String,
    val category: String,
    val license: String,
    val subsets: Set<String>,
)

internal data class FontLoadProgress(val label: String, val fraction: Double?)

internal sealed interface FontCatalogState {
    data object Loading : FontCatalogState
    data class Ready(val fonts: List<FontCatalogEntry>) : FontCatalogState
    data class Failed(val message: String) : FontCatalogState
}

internal object FontCatalog {
    private val tr = I18n.scope("blackaddons.global_font_screen")

    private val cache = ContentCache("fonts", 256L * 1024L * 1024L)

    @Volatile
    var state: FontCatalogState = FontCatalogState.Loading
        private set

    @Volatile private var loading = false
    @Volatile private var libraryLoaded = false
    @Volatile private var library: List<InstalledFont> = emptyList()

    @Synchronized
    fun load() {
        if (state is FontCatalogState.Ready || loading) return
        loading = true
        state = FontCatalogState.Loading
        OffThread.run(
            name = "font-catalog",
            work = { loadCatalog() },
            onFailure = { FontCatalogState.Failed(it.message ?: tr("catalog_load_failed", "Font catalog could not be loaded")) },
            onResult = {
                state = it
                loading = false
            },
        )
    }

    fun install(
        entry: FontCatalogEntry,
        onProgress: (FontLoadProgress) -> Unit = {},
        done: (Result<InstalledFont>) -> Unit,
    ) {
        OffThread.run(
            name = "font-download",
            work = { runCatching { installBlocking(entry, onProgress) } },
            onFailure = { Result.failure(it) },
            onResult = done,
        )
    }

    fun install(id: String, done: (Result<InstalledFont>) -> Unit) {
        OffThread.run(
            name = "font-download",
            work = {
                runCatching {
                    val fonts = (state as? FontCatalogState.Ready ?: loadCatalog() as? FontCatalogState.Ready)?.fonts
                        ?: error(tr("catalog_load_failed", "Font catalog could not be loaded"))
                    val entry = fonts.firstOrNull { it.id == id } ?: error(tr("font_not_in_catalog", "Fontsource has no font \"%s\"", id))
                    installBlocking(entry) {}
                }
            },
            onFailure = { Result.failure(it) },
            onResult = done,
        )
    }

    fun import(
        path: Path,
        onProgress: (FontLoadProgress) -> Unit = {},
        done: (Result<InstalledFont>) -> Unit,
    ) {
        OffThread.run(
            name = "font-import",
            work = { runCatching { importBlocking(path, onProgress) } },
            onFailure = { Result.failure(it) },
            onResult = done,
        )
    }

    fun uninstall(font: InstalledFont, done: (Result<InstalledFont>) -> Unit) {
        OffThread.run(
            name = "font-uninstall",
            work = { runCatching { uninstallBlocking(font) } },
            onFailure = { Result.failure(it) },
            onResult = done,
        )
    }

    /**
     * A pinned Fontsource subset a cosmetic name uses, through the same cache as the global font. Blocking, so call it
     * off the client thread. Null when the file can't be downloaded.
     */
    internal fun nickFile(url: String): Path? {
        nickFileKey(url)?.let { key -> cache.path(key)?.let { return it } }
        if (!url.startsWith(NICK_FILE_HOST)) return null
        val bytes = Http.bytes(url, MAX_FONT_BYTES) ?: return null
        if (bytes.size < MIN_FONT_BYTES || FileFormat.detect(bytes) != FileFormat.TRUE_TYPE) return null
        val key = cache.put(bytes)
        rememberNickFile(url, key)
        return cache.path(key)
    }

    fun paths(cacheKeys: List<String>): List<Path>? {
        val paths = cacheKeys.map { cache.path(it) ?: return null }
        return paths.takeIf { it.isNotEmpty() }
    }

    fun installed(): List<InstalledFont> {
        if (libraryLoaded) return library
        synchronized(this) {
            if (!libraryLoaded) {
                library = readLibrary()
                libraryLoaded = true
            }
            return library
        }
    }

    fun track(font: InstalledFont) {
        if (font.cacheKeys.isNotEmpty() && paths(font.cacheKeys) != null) rememberFont(font)
    }

    private fun loadCatalog(): FontCatalogState {
        val cached = catalogPath()
        val fresh = Files.exists(cached) && System.currentTimeMillis() - Files.getLastModifiedTime(cached).toMillis() < CATALOG_TTL_MS
        val text = if (fresh) Disk.read(cached) else {
            val response = Platform.http.getBlocking(CATALOG_URL)
            if (response?.status == 200) response.body.also { body ->
                runCatching { Disk.write(cached, body) }
            } else Disk.read(cached)
        } ?: return FontCatalogState.Failed(tr("fontsource_unavailable", "Fontsource is unavailable and no cached catalog exists"))

        val fonts = JsonParser.parseString(text).asJsonArray.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            val family = obj.string("family") ?: return@mapNotNull null
            FontCatalogEntry(
                id = id,
                family = family,
                category = obj.string("category") ?: "font",
                license = obj.string("license") ?: "Open source",
                subsets = obj.array("subsets").strings().toSet(),
            )
        }.sortedBy { it.family.lowercase() }
        recoverInstalledFonts(fonts)
        return if (fonts.isEmpty()) FontCatalogState.Failed(tr("empty_catalog", "Fontsource returned an empty catalog"))
        else FontCatalogState.Ready(fonts)
    }

    private fun installBlocking(
        entry: FontCatalogEntry,
        onProgress: (FontLoadProgress) -> Unit,
    ): InstalledFont {
        onProgress(FontLoadProgress(tr("loading_metadata", "Loading %s metadata...", entry.family), 0.05))
        val response = Platform.http.getBlocking("$CATALOG_URL/${entry.id}")
            ?: error(tr("fontsource_no_answer", "Fontsource did not answer"))
        require(response.status == 200) { tr("fontsource_http", "Fontsource returned HTTP %d", response.status) }
        val detail = JsonParser.parseString(response.body).asJsonObject
        val npmVersion = detail.string("npmVersion")
            ?: error(tr("missing_version", "Fontsource response has no version"))
        val variants = detail.getAsJsonObject("variants")
            ?: error(tr("missing_variants", "Fontsource response has no variants"))
        val weight = variants.keySet().mapNotNull(String::toIntOrNull).minByOrNull { abs(it - 400) }
            ?: error(tr("no_weight", "Font has no usable weight"))
        val styles = variants.getAsJsonObject(weight.toString())
            ?: error(tr("weight_unavailable", "Font weight is unavailable"))
        val styleName = if (styles.has("normal")) "normal" else styles.keySet().firstOrNull()
            ?: error(tr("no_style", "Font has no usable style"))
        val style = styles.getAsJsonObject(styleName)
            ?: error(tr("style_unavailable", "Font style is unavailable"))

        val wanted = selectSubsets(entry.subsets, Platform.locale.current)
        val installKey = "${entry.id}:$npmVersion:$weight:$styleName:${wanted.joinToString(",")}"
        cachedInstallation(installKey)?.let { keys ->
            onProgress(FontLoadProgress(tr("loaded_from_cache", "Loaded %s from cache", entry.family), 1.0))
            return InstalledFont("$FONTSOURCE_KEY_PREFIX${entry.id}", entry.family, keys).also(::rememberFont)
        }
        val keys = wanted.mapIndexed { index, subset ->
            val rawUrl = style.getAsJsonObject(subset)?.getAsJsonObject("url")?.get("ttf")?.asString
                ?: error(tr("missing_ttf", "Fontsource has no TTF for %s", subset))
            val url = rawUrl.replace("@latest/", "@$npmVersion/")
            val uri = URI.create(url)
            require(uri.scheme == "https" && uri.host == "cdn.jsdelivr.net") {
                tr("unsafe_url", "Fontsource returned an unsafe download URL")
            }
            val start = DOWNLOAD_START + DOWNLOAD_SHARE * index / wanted.size
            val share = DOWNLOAD_SHARE / wanted.size
            val bytes = Http.bytes(url, MAX_FONT_BYTES) { received, total ->
                val fraction = total?.takeIf { it > 0L }?.let { (received.toDouble() / it).coerceIn(0.0, 1.0) }
                onProgress(FontLoadProgress(
                    tr("downloading_subset", "Downloading %s · %s", entry.family, subset),
                    fraction?.let { start + share * it },
                ))
            } ?: error(tr("download_failed", "Could not download %s", subset))
            require(bytes.size >= MIN_FONT_BYTES) { tr("invalid_subset_file", "%s font file is invalid", subset) }
            require(FileFormat.detect(bytes) == FileFormat.TRUE_TYPE) {
                tr("not_true_type_subset", "%s is not a TrueType font", subset)
            }
            cache.put(bytes)
        }
        onProgress(FontLoadProgress(tr("saving_font", "Saving %s...", entry.family), 0.95))
        rememberInstallation(installKey, keys)
        return InstalledFont("$FONTSOURCE_KEY_PREFIX${entry.id}", entry.family, keys).also(::rememberFont).also {
            onProgress(FontLoadProgress(tr("ready", "%s is ready", entry.family), 1.0))
        }
    }

    private fun importBlocking(path: Path, onProgress: (FontLoadProgress) -> Unit): InstalledFont {
        onProgress(FontLoadProgress(tr("checking_local", "Checking local font..."), 0.1))
        require(Files.isRegularFile(path)) { tr("selected_missing", "Selected font does not exist") }
        val size = Files.size(path)
        require(size in MIN_FONT_BYTES.toLong()..MAX_FONT_BYTES.toLong()) {
            tr("invalid_file_size", "Font file size is invalid")
        }
        onProgress(FontLoadProgress(tr("reading_file", "Reading %s...", path.fileName), 0.35))
        val bytes = Files.readAllBytes(path)
        require(FileFormat.detect(bytes)?.isFont == true) {
            tr("unsupported_font_file", "This is not a supported TrueType or OpenType font")
        }
        val name = path.fileName.toString().substringBeforeLast('.').ifBlank { tr("local_font", "Local font") }
        onProgress(FontLoadProgress(tr("saving_font", "Saving %s...", name), 0.8))
        val key = cache.put(bytes)
        return InstalledFont("local:$key", name, listOf(key)).also(::rememberFont).also {
            onProgress(FontLoadProgress(tr("ready", "%s is ready", name), 1.0))
        }
    }

    @Synchronized
    private fun uninstallBlocking(font: InstalledFont): InstalledFont {
        val installedFont = installed().firstOrNull { it.key == font.key }
            ?: error(tr("font_not_installed", "%s is not installed", font.displayName))
        val remaining = library.filterNot { it.key == installedFont.key }

        val installations = readInstallations()
        if (installedFont.key.startsWith(FONTSOURCE_KEY_PREFIX)) {
            val id = installedFont.key.removePrefix(FONTSOURCE_KEY_PREFIX)
            installations.entrySet().removeIf { it.key.substringBefore(':') == id }
            Disk.write(installationsPath(), installations.toString())
        }
        writeLibrary(remaining)

        val referenced = remaining.flatMapTo(HashSet(), InstalledFont::cacheKeys)
        readNickFiles().entrySet().forEach { (_, value) -> value.takeIf { it.isJsonPrimitive }?.asString?.let(referenced::add) }
        installations.entrySet().forEach { (_, value) ->
            value.takeIf { it.isJsonArray }?.asJsonArray?.strings()?.let(referenced::addAll)
        }
        installedFont.cacheKeys.filterNot(referenced::contains).forEach(cache::remove)
        return installedFont
    }

    internal fun selectSubsets(available: Set<String>, locale: String): List<String> {
        val wanted = linkedSetOf("latin", "latin-ext")
        when (locale.substringBefore('_').lowercase()) {
            "ru", "uk", "bg", "mk", "sr" -> wanted += listOf("cyrillic", "cyrillic-ext")
            "el" -> wanted += listOf("greek", "greek-ext")
            "vi" -> wanted += "vietnamese"
        }
        val found = wanted.filter { it in available }
        return found.ifEmpty { listOf(available.firstOrNull() ?: error(tr("no_subsets", "Font has no subsets"))) }
    }

    @Synchronized
    private fun cachedInstallation(key: String): List<String>? {
        val root = readInstallations()
        val keys = root.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.strings() ?: return null
        return keys.takeIf { it.isNotEmpty() && paths(it) != null }
    }

    @Synchronized
    private fun rememberInstallation(key: String, keys: List<String>) {
        val path = installationsPath()
        val root = readInstallations()
        root.add(key, JsonArray().apply { keys.forEach(::add) })
        Disk.write(path, root.toString())
    }

    @Synchronized
    private fun rememberFont(font: InstalledFont) = rememberFonts(listOf(font))

    @Synchronized
    private fun rememberFonts(fonts: List<InstalledFont>) {
        val latest = fonts.associateBy(InstalledFont::key)
        writeLibrary(installed().filterNot { it.key in latest } + latest.values)
    }

    private fun writeLibrary(fonts: List<InstalledFont>) {
        val sorted = fonts.sortedWith(LIBRARY_ORDER)
        if (sorted == library) return
        val data = JsonArray().apply {
            sorted.forEach { installed ->
                add(JsonObject().apply {
                    addProperty("key", installed.key)
                    addProperty("name", installed.displayName)
                    add("cacheKeys", JsonArray().apply { installed.cacheKeys.forEach(::add) })
                })
            }
        }
        Disk.write(libraryPath(), data.toString())
        library = sorted
    }

    @Synchronized
    private fun nickFileKey(url: String): String? = readNickFiles().get(url)?.takeIf { it.isJsonPrimitive }?.asString

    @Synchronized
    private fun rememberNickFile(url: String, key: String) {
        val root = readNickFiles()
        root.addProperty(url, key)
        Disk.write(nickFilesPath(), root.toString())
    }

    private fun readNickFiles(): JsonObject =
        Disk.read(nickFilesPath())?.let { text -> parseJsonObject(text) }
            ?: JsonObject()

    private fun readInstallations(): JsonObject =
        Disk.read(installationsPath())?.let { text ->
            parseJsonObject(text)
        } ?: JsonObject()

    private fun readLibrary(): List<InstalledFont> {
        val text = Disk.read(libraryPath()) ?: return emptyList()
        val data = runCatching { JsonParser.parseString(text).asJsonArray }.getOrNull() ?: return emptyList()
        return data.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val key = obj.string("key") ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            val cacheKeys = obj.array("cacheKeys").strings()
            InstalledFont(key, name, cacheKeys).takeIf { cacheKeys.isNotEmpty() && paths(cacheKeys) != null }
        }.distinctBy(InstalledFont::key).sortedWith(LIBRARY_ORDER)
    }

    private fun recoverInstalledFonts(catalog: List<FontCatalogEntry>) {
        val text = Disk.read(installationsPath()) ?: return
        val installations = parseJsonObject(text) ?: return
        val byId = catalog.associateBy(FontCatalogEntry::id)
        val recovered = installations.entrySet().mapNotNull { (installation, value) ->
            val entry = byId[installation.substringBefore(':')] ?: return@mapNotNull null
            val keys = value.takeIf { it.isJsonArray }?.asJsonArray?.strings() ?: return@mapNotNull null
            InstalledFont("$FONTSOURCE_KEY_PREFIX${entry.id}", entry.family, keys)
                .takeIf { keys.isNotEmpty() && paths(keys) != null }
        }
        rememberFonts(recovered)
    }

    private fun catalogPath(): Path = Platform.configDir.resolve("fontsource-catalog.json")
    private fun installationsPath(): Path = Platform.configDir.resolve("font-installations.json")
    private fun libraryPath(): Path = Platform.configDir.resolve("font-library.json")
    private fun nickFilesPath(): Path = Platform.configDir.resolve("font-nick-files.json")

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.array(name: String): JsonArray = get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    private fun JsonArray.strings(): List<String> = mapNotNull { it.takeIf { value -> value.isJsonPrimitive }?.asString }

    private const val CATALOG_URL = "https://api.fontsource.org/v1/fonts"
    private const val NICK_FILE_HOST = "https://cdn.jsdelivr.net/fontsource/fonts/"
    private const val CATALOG_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    private const val MIN_FONT_BYTES = 1024
    private const val MAX_FONT_BYTES = 24 * 1024 * 1024
    private const val DOWNLOAD_START = 0.15
    private const val DOWNLOAD_SHARE = 0.75
    const val FONTSOURCE_KEY_PREFIX = "fontsource:"
    private val LIBRARY_ORDER = compareBy<InstalledFont>({ it.displayName.lowercase() }, { it.key })
}
