package org.blackaddons.core.emoji

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.util.graphemes

internal sealed interface EmojiSegment {
    data class Text(val text: String) : EmojiSegment

    data class Glyph(val glyph: Int, val text: String) : EmojiSegment

    data class Custom(val id: String, val name: String, val animated: Boolean) : EmojiSegment
}

internal data class CatalogEmoji(val glyph: Int, val names: List<String>)

internal data class EmojiCategory(val id: String, val label: String, val emoji: List<CatalogEmoji>)

/**
 * Twemoji (Discord's set) from an atlas the `generateEmoji` Gradle task builds, with Discord's names. The atlas is also
 * a vanilla font: one PUA char per glyph in [FONT].
 */
internal object Emoji {

    /** Glyph at [PUA] + index. */
    const val FONT = "blackaddons:emoji"

    fun sheetPath(sheet: Int): String = "assets/blackaddons/textures/font/emoji_$sheet.png"

    private val CUSTOM = Regex("<(a?):([A-Za-z0-9_]{2,32}):(\\d{15,21})>")
    private val SHORTCODE = Regex("(?<![<a]):([A-Za-z0-9_+-]{2,40}):(?!\\d)")

    private const val VS16 = 0xFE0F

    private class Node {
        val next = HashMap<Int, Node>(4)
        var glyph = -1
    }

    private class Catalog(
        val pua: Int,
        val cell: Int,
        val grid: Int,
        val codes: List<String>,
        val names: Map<String, Int>,
        val categories: List<EmojiCategory>,
        val trie: Node,
    )

    private val catalog: Catalog by lazy { load() }

    val categories: List<EmojiCategory> get() = catalog.categories

    fun pua(glyph: Int): Char = (catalog.pua + glyph).toChar()

    /** `[sheet, x, y, size]`. */
    fun cellOf(glyph: Int): IntArray {
        val perSheet = catalog.grid * catalog.grid
        val slot = glyph % perSheet
        return intArrayOf(
            glyph / perSheet, slot % catalog.grid * catalog.cell, slot / catalog.grid * catalog.cell,
            catalog.cell
        )
    }

    fun sheetSize(): Int = catalog.grid * catalog.cell

    fun glyphFor(name: String): Int? = catalog.names[name]

    fun segments(text: String): List<EmojiSegment> {
        val out = ArrayList<EmojiSegment>()
        var at = 0
        for (match in CUSTOM.findAll(text)) {
            if (match.range.first > at) unicode(text.substring(at, match.range.first), out)
            out += EmojiSegment.Custom(match.groupValues[3], match.groupValues[2], match.groupValues[1] == "a")
            at = match.range.last + 1
        }
        if (at < text.length) unicode(text.substring(at), out)
        return out
    }

    /** Composer steps. A custom emoji token is one step, like in Discord. */
    fun cells(text: String): List<String> {
        if ('<' !in text) return text.graphemes()
        val out = ArrayList<String>()
        var at = 0
        for (match in CUSTOM.findAll(text)) {
            out += text.substring(at, match.range.first).graphemes()
            out += match.value
            at = match.range.last + 1
        }
        out += text.substring(at).graphemes()
        return out
    }

    fun hasAny(text: String): Boolean = segments(text).any { it !is EmojiSegment.Text }

    fun expandShortcodes(text: String, custom: List<CustomEmoji>): String {
        if (':' !in text) return text
        val byName = custom.associateBy { it.name }
        return SHORTCODE.replace(text) { match ->
            val name = match.groupValues[1]
            catalog.names[name]?.let { glyph -> glyphText(glyph) }
                ?: byName[name]?.token
                ?: match.value
        }
    }

    fun namesStartingWith(prefix: String, limit: Int): List<String> =
        catalog.names.keys.asSequence()
            .filter { it.startsWith(prefix) }
            .sortedWith(compareBy({ it.length }, { it }))
            .take(limit)
            .toList()

    // Glyph back to text from its Twemoji name, with FE0F after single characters that default to text.
    private val glyphTexts = HashMap<Int, String>()

    fun glyphText(glyph: Int): String = synchronized(glyphTexts) {
        glyphTexts.getOrPut(glyph) {
            val points = catalog.codes[glyph].split('-').map { it.toInt(16) }
            buildString {
                points.forEach { appendCodePoint(it) }
                if (points.size == 1 && !Character.isEmojiPresentation(points[0])) appendCodePoint(VS16)
            }
        }
    }

    private fun unicode(text: String, out: MutableList<EmojiSegment>) {
        val trie = catalog.trie
        val run = StringBuilder()
        var i = 0
        while (i < text.length) {
            val (glyph, end) = longestMatch(trie, text, i)
            if (glyph >= 0 && presentsAsEmoji(text, i, end)) {
                if (run.isNotEmpty()) {
                    out += EmojiSegment.Text(run.toString())
                    run.clear()
                }
                out += EmojiSegment.Glyph(glyph, text.substring(i, end))
                i = end
            } else {
                val cp = text.codePointAt(i)
                run.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }
        if (run.isNotEmpty()) out += EmojiSegment.Text(run.toString())
    }

    // Characters that default to text (©, ™, ♥, ⚧) are emoji only with FE0F.
    private fun presentsAsEmoji(text: String, start: Int, end: Int): Boolean {
        val first = text.codePointAt(start)
        return start + Character.charCount(first) != end || Character.isEmojiPresentation(first)
    }

    // Skips variation selectors with no trie edge, people type both spellings anyway.
    private fun longestMatch(root: Node, text: String, start: Int): Pair<Int, Int> {
        var node = root
        var i = start
        var best = -1
        var bestEnd = start
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val next = node.next[cp]
            if (next == null) {
                if (cp == VS16 && node !== root) {
                    i += Character.charCount(cp)
                    if (node.glyph >= 0) bestEnd = i
                    continue
                }
                break
            }
            node = next
            i += Character.charCount(cp)
            if (node.glyph >= 0) {
                best = node.glyph
                bestEnd = i
            }
        }
        return best to bestEnd
    }

    private fun resource(): String =
        Emoji::class.java.getResourceAsStream("/assets/blackaddons/emoji/catalog.json")
            ?.use { it.readBytes().decodeToString() }
            ?: error("emoji catalog missing from the jar")

    private fun load(): Catalog {
        val json = JsonParser.parseString(resource()).asJsonObject
        val glyphs = json.getAsJsonArray("glyphs").map { it.asString }

        val trie = Node()
        glyphs.forEachIndexed { index, code ->
            val points = code.split('-').map { it.toInt(16) }
            insert(trie, points, index)
            if (VS16 in points) insert(trie, points.filter { it != VS16 }, index)
        }

        val names = json.getAsJsonObject("names").entrySet().associate { it.key to it.value.asInt }
        val categories = json.getAsJsonArray("categories").map { element ->
            val category = element.asJsonObject
            EmojiCategory(
                category.get("id").asString,
                category.get("label").asString,
                category.getAsJsonArray("emoji").map { entry ->
                    val array = entry.asJsonArray
                    CatalogEmoji(array[0].asInt, (1 until array.size()).map { array[it].asString })
                },
            )
        }

        return Catalog(json.int("pua"), json.int("cell"), json.int("grid"), glyphs, names, categories, trie)
    }

    private fun insert(root: Node, points: List<Int>, glyph: Int) {
        var node = root
        for (cp in points) node = node.next.getOrPut(cp) { Node() }
        if (node.glyph < 0) node.glyph = glyph
    }

    private fun JsonObject.int(key: String): Int = get(key).asInt
}
