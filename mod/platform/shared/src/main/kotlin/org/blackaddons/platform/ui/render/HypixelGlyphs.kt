package org.blackaddons.platform.ui.render

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.util.concurrent.LoadOnce
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.api.util.io.str
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.log.Log
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.io.path.exists

/**
 * Hypixel's stat and gemstone icons. Skyblock text writes them as private-use characters that only the server's
 * resource pack can draw, so item lore showed empty boxes in their place. The icons come from that pack: the copy
 * Minecraft kept from the last visit to Hypixel, otherwise the current one from Hypixel's own pack list. We only keep
 * the font sheets, in the cache folder, the pack itself isn't stored.
 */
internal object HypixelGlyphs {

    private const val PACKS_URL = "${Backend.HYPIXEL_URL}/v2/resources/packs"
    private const val PACK_ID = "SkyBlock"
    private const val PACK_HOST = "resourcepacks.hypixel.net/$PACK_ID/"
    private const val FONT_JSON = "assets/minecraft/font/default.json"
    private const val INDEX = "index.json"
    private const val MAX_PACK_BYTES = 64 * 1024 * 1024
    private const val PUA_FIRST = 0xE000
    private const val PUA_LAST = 0xF8FF
    private const val DEFAULT_HEIGHT = 8
    private const val RETRY_MS = 10 * 60 * 1000L
    private const val ALPHA_SHIFT = 24

    /**
     * One icon: the [srcW] x [srcH] cell at ([srcX], [srcY]) of the [sheet] image, drawn [height] game pixels tall with
     * [ascent] of them above the baseline, like Minecraft's bitmap fonts do.
     */
    class Glyph(val sheet: String, val srcX: Int, val srcY: Int, val srcW: Int, val srcH: Int, val height: Int, val ascent: Int)

    private val dir: Path get() = Platform.configDir.resolve("cache").resolve("hypixel-glyphs")

    private val table = LoadOnce<Unit, Map<Int, Glyph>>("hypixel-glyphs", retryAfterMs = RETRY_MS) { load() }

    /** Null while loading, when no pack could be found, and for every character the pack doesn't draw. */
    fun of(codePoint: Int): Glyph? =
        if (codePoint in PUA_FIRST..PUA_LAST) table[Unit]?.get(codePoint) else null

    /** True for a character only Hypixel's pack can draw, whether its icon is in yet or not. */
    fun isPrivateUse(codePoint: Int): Boolean = codePoint in PUA_FIRST..PUA_LAST

    private fun load(): Map<Int, Glyph>? {
        readIndex()?.let { return it }
        val pack = localPack() ?: downloadPack() ?: return null
        return try {
            extract(pack.path)
        } finally {
            if (pack.temporary) Files.deleteIfExists(pack.path)
        }
    }

    private class Pack(val path: Path, val temporary: Boolean)

    // Minecraft logs each server pack it downloads, newest last.
    private fun localPack(): Pack? {
        val downloads = Platform.gameDir.resolve("downloads")
        val log = downloads.resolve("log.json").takeIf { it.exists() } ?: return null
        val names = runCatching { Files.readAllLines(log) }.getOrNull().orEmpty().mapNotNull { line ->
            val entry = parseJsonObject(line) ?: return@mapNotNull null
            if (entry.str("url")?.contains(PACK_HOST) != true) return@mapNotNull null
            entry.getAsJsonObject("file")?.str("name")
        }
        return names.asReversed().map { downloads.resolve(it.replace('\\', '/')) }.firstOrNull { it.exists() }?.let { Pack(it, false) }
    }

    private fun downloadPack(): Pack? {
        val response = Http.get(PACKS_URL, authenticated = true)?.takeIf { it.status == 200 } ?: return null
        val url = runCatching {
            JsonParser.parseString(response.body).asJsonObject.getAsJsonArray("packs")
                .map { it.asJsonObject }.first { it.str("id") == PACK_ID }
                .getAsJsonArray("versions").last().asJsonObject.str("url")
        }.getOrNull() ?: return null
        val bytes = Http.bytes(url, maxBytes = MAX_PACK_BYTES) ?: return null
        Files.createDirectories(dir)
        val file = Files.createTempFile(dir, "pack", ".zip")
        Files.write(file, bytes)
        return Pack(file, true)
    }

    private fun extract(pack: Path): Map<Int, Glyph>? = ZipFile(pack.toFile()).use { zip ->
        val font = zip.getEntry(FONT_JSON)?.let { zip.getInputStream(it).reader().use(JsonParser::parseReader).asJsonObject }
            ?: return null
        val glyphs = HashMap<Int, Glyph>()
        val sheets = HashMap<String, ByteArray>()
        for (provider in font.getAsJsonArray("providers").map { it.asJsonObject }) {
            if (provider.str("type") != "bitmap") continue
            val file = provider.str("file") ?: continue
            val rows = provider.getAsJsonArray("chars")?.map { it.asString } ?: continue
            if (rows.none { row -> row.any { it.code in PUA_FIRST..PUA_LAST } }) continue
            val texture = zip.getEntry(texturePath(file)) ?: continue
            val bytes = zip.getInputStream(texture).use { it.readBytes() }
            val image = ImageIO.read(bytes.inputStream()) ?: continue
            val sheetName = file.replace(':', '_').replace('/', '_')
            sheets[sheetName] = bytes
            val cellW = image.width / rows.maxOf { it.length }
            val cellH = image.height / rows.size
            rows.forEachIndexed { row, chars ->
                chars.forEachIndexed { column, ch ->
                    if (ch.code !in PUA_FIRST..PUA_LAST) return@forEachIndexed
                    val width = inkWidth(image, column * cellW, row * cellH, cellW, cellH)
                    if (width == 0) return@forEachIndexed
                    glyphs[ch.code] = Glyph(
                        sheetName, column * cellW, row * cellH, width, cellH,
                        provider.get("height")?.asInt ?: DEFAULT_HEIGHT, provider.get("ascent")?.asInt ?: 0,
                    )
                }
            }
        }
        if (glyphs.isEmpty()) return null
        save(sheets, glyphs)
        glyphs.mapValues { (_, g) -> g.copyWithSheet(sheetPath(g.sheet)) }
    }

    // Same as Minecraft measures a bitmap glyph: up to its rightmost column with any ink.
    private fun inkWidth(image: BufferedImage, x: Int, y: Int, w: Int, h: Int): Int {
        for (column in w - 1 downTo 0) {
            for (row in 0 until h) if (image.getRGB(x + column, y + row) ushr ALPHA_SHIFT != 0) return column + 1
        }
        return 0
    }

    private fun save(sheets: Map<String, ByteArray>, glyphs: Map<Int, Glyph>) {
        val index = JsonObject().apply {
            for ((code, g) in glyphs) {
                add(code.toString(), JsonObject().apply {
                    addProperty("sheet", g.sheet)
                    addProperty("x", g.srcX)
                    addProperty("y", g.srcY)
                    addProperty("w", g.srcW)
                    addProperty("h", g.srcH)
                    addProperty("height", g.height)
                    addProperty("ascent", g.ascent)
                })
            }
        }
        runCatching {
            Disk.writeAll {
                for ((name, bytes) in sheets) file(dir.resolve("$name.png")) { Files.write(it, bytes) }
                file(dir.resolve(INDEX)) { Files.writeString(it, GsonBuilder().create().toJson(index)) }
            }
        }.onFailure { Log.main.warn("Could not cache Hypixel's text icons", it) }
    }

    private fun readIndex(): Map<Int, Glyph>? {
        val text = runCatching { Disk.read(dir.resolve(INDEX)) }.getOrNull() ?: return null
        return runCatching {
            JsonParser.parseString(text).asJsonObject.entrySet().associate { (code, value) ->
                val g = value.asJsonObject
                val sheet = g.str("sheet")!!
                code.toInt() to Glyph(
                    sheetPath(sheet), g.get("x").asInt, g.get("y").asInt, g.get("w").asInt, g.get("h").asInt,
                    g.get("height").asInt, g.get("ascent").asInt,
                )
            }
        }.getOrNull()?.takeIf { map -> map.isNotEmpty() && map.values.all { Path.of(it.sheet.removePrefix(Render.FILE_SCHEME)).exists() } }
    }

    private fun sheetPath(name: String): String = Render.FILE_SCHEME + dir.resolve("$name.png").toAbsolutePath()

    private fun Glyph.copyWithSheet(path: String) = Glyph(path, srcX, srcY, srcW, srcH, height, ascent)

    // `namespace:path` names a texture under that namespace's textures folder.
    private fun texturePath(file: String): String {
        val namespace = file.substringBefore(':', "minecraft")
        val path = file.substringAfter(':')
        return "assets/$namespace/textures/$path"
    }
}
