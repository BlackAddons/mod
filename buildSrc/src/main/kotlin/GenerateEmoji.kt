import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.inject.Inject

abstract class GenerateEmoji : DefaultTask() {
    @get:Internal
    abstract val root: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @TaskAction
    fun generate() {
        val platform = root.get().asFile.resolve("mod/platform/shared/src/main/resources/assets/blackaddons")
        val core = root.get().asFile.resolve("mod/core/src/main/resources/assets/blackaddons")

        val pngs = twemojiPngs()
        val codes = pngs.keys.sorted()
        val index = codes.withIndex().associate { (i, code) -> code to i }
        check(codes.size <= 0xF8FF - PUA + 1) { "more glyphs than the private-use area holds" }

        val sheets = (codes.size + GRID * GRID - 1) / (GRID * GRID)
        platform.resolve("textures/font").mkdirs()
        val providers = (0 until sheets).map { sheet ->
            val atlas = Rgba(CELL * GRID, CELL * GRID)
            val rows = (0 until GRID).map { row ->
                buildString {
                    for (column in 0 until GRID) {
                        val i = sheet * GRID * GRID + row * GRID + column
                        if (i < codes.size) {
                            atlas.paste(Rgba.read(pngs.getValue(codes[i])).resizeLanczos(CELL, CELL), column * CELL, row * CELL)
                            append((PUA + i).toChar())
                        } else {
                            append('\u0000')
                        }
                    }
                }
            }
            /* A palette per sheet: a fifth of the size, and nobody can see the difference at chat size. */
            atlas.quantizeOctree(256).write(platform.resolve("textures/font/emoji_$sheet.png"))
            mapOf("type" to "bitmap", "file" to "blackaddons:font/emoji_$sheet.png", "ascent" to 8, "height" to 9, "chars" to rows)
        }
        platform.resolve("font/emoji.json").writeText(pythonJson(mapOf("providers" to providers), indent = 2) + "\n")

        val tables = discordTables()
        val names = LinkedHashMap<String, Int>()
        val categories = CATEGORIES.map { (category, label) ->
            val entries = LinkedHashMap<Int, MutableList<String>>()
            for ((name, char) in tables.getValue(category)) {
                val glyph = index[codeOf(char)] ?: continue
                names.putIfAbsent(name, glyph)
                /* Skin tones stay typeable by name but don't crowd the picker. */
                if (SKIN_TONES.any { it in codes[glyph] }) continue
                entries.getOrPut(glyph) { mutableListOf() } += name
            }
            mapOf("id" to category, "label" to label, "emoji" to entries.map { (glyph, aliases) -> listOf(glyph) + aliases })
        }

        val catalog = mapOf(
            "twemoji" to TWEMOJI_VERSION,
            "cell" to CELL,
            "grid" to GRID,
            "pua" to PUA,
            "glyphs" to codes,
            "names" to names,
            "categories" to categories,
        )
        core.resolve("emoji").mkdirs()
        core.resolve("emoji/catalog.json").writeText(pythonJson(catalog, indent = null) + "\n")

        logger.lifecycle("${codes.size} glyphs on $sheets sheet(s), ${names.size} names")
    }

    private fun fetch(url: String, name: String): File {
        val target = temporaryDir.resolve(name)
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val response = client.send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.ofFile(target.toPath()))
        check(response.statusCode() == 200) { "GET $url answered ${response.statusCode()}" }
        return target
    }

    private fun twemojiPngs(): Map<String, ByteArray> {
        val archive = fetch("https://github.com/jdecked/twemoji/archive/refs/tags/v$TWEMOJI_VERSION.tar.gz", "twemoji.tar.gz")
        val pngs = HashMap<String, ByteArray>()
        archives.tarTree(archives.gzip(archive)).visit {
            if (!isDirectory && "/assets/72x72/" in relativePath.pathString && name.endsWith(".png")) {
                pngs[name.removeSuffix(".png")] = open().use { it.readBytes() }
            }
        }
        return pngs
    }

    private fun discordTables(): Map<String, List<Pair<String, String>>> {
        val archive = fetch("https://registry.npmjs.org/discord-emoji/-/discord-emoji-$DISCORD_EMOJI_VERSION.tgz", "discord-emoji.tgz")
        var source: String? = null
        archives.tarTree(archives.gzip(archive)).visit {
            if (relativePath.pathString == "package/esm/mod.js") source = open().use { it.readBytes().decodeToString() }
        }
        val js = checkNotNull(source) { "package/esm/mod.js missing from discord-emoji $DISCORD_EMOJI_VERSION" }
        return CATEGORIES.associate { (category, _) ->
            val start = js.indexOf("const $category = {").also { check(it >= 0) { "no table for $category" } }
            val body = js.substring(js.indexOf('{', start), js.indexOf("};", start) + 1)
            /* A JavaScript object literal: trailing commas are fine there, unlike JSON. */
            val table = JsonSlurper().parseText(body.replace(Regex(",\\s*}"), "}")) as Map<*, *>
            category to table.map { (name, char) -> name as String to char as String }
        }
    }

    private companion object {
        const val TWEMOJI_VERSION = "17.0.2"
        const val DISCORD_EMOJI_VERSION = "2.5.3"

        const val CELL = 32
        const val GRID = 16
        const val PUA = 0xE000
        val SKIN_TONES = listOf("1f3fb", "1f3fc", "1f3fd", "1f3fe", "1f3ff")

        val CATEGORIES = listOf(
            "people" to "People",
            "nature" to "Nature",
            "food" to "Food & drink",
            "activity" to "Activities",
            "travel" to "Travel & places",
            "objects" to "Objects",
            "symbols" to "Symbols",
            "flags" to "Flags",
        )

        /* Twemoji's file name for an emoji: its code points, with FE0F dropped unless it's a ZWJ sequence. */
        fun codeOf(char: String): String =
            (if ("‍" in char) char else char.replace("️", ""))
                .codePoints().toArray().joinToString("-") { it.toString(16) }

        fun pythonJson(value: Any, indent: Int?): String = StringBuilder().apply { appendJson(value, indent, 1) }.toString()

        private fun StringBuilder.appendJson(value: Any, indent: Int?, depth: Int) {
            when (value) {
                is String -> appendJsonString(value)
                is Int -> append(value)
                is List<*> -> appendJsonContainer('[', ']', value, indent, depth) { appendJson(it!!, indent, depth + 1) }
                is Map<*, *> -> appendJsonContainer('{', '}', value.entries, indent, depth) { (key, item) ->
                    appendJsonString(key as String)
                    append(if (indent == null) ":" else ": ")
                    appendJson(item!!, indent, depth + 1)
                }
                else -> error("no JSON for ${value::class}")
            }
        }

        private fun <T> StringBuilder.appendJsonContainer(
            open: Char,
            close: Char,
            items: Collection<T>,
            indent: Int?,
            depth: Int,
            item: StringBuilder.(T) -> Unit,
        ) {
            append(open)
            if (items.isNotEmpty()) {
                val separator = if (indent == null) "," else ",\n" + " ".repeat(indent * depth)
                if (indent != null) append('\n').append(" ".repeat(indent * depth))
                items.forEachIndexed { i, it ->
                    if (i > 0) append(separator)
                    item(it)
                }
                if (indent != null) append('\n').append(" ".repeat(indent * (depth - 1)))
            }
            append(close)
        }

        private fun StringBuilder.appendJsonString(value: String) {
            append('"')
            for (c in value) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    in ' '..'~' -> append(c)
                    else -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                }
            }
            append('"')
        }
    }
}
