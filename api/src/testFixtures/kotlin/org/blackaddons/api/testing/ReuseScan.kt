package org.blackaddons.api.testing

import org.blackaddons.api.testing.ReuseScan.MIN_BODY
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

/** A main Kotlin file; [path] is repo-relative with `/` separators. */
class SourceFile(val path: String, val text: String) {
    val lines: List<String> by lazy { text.lines() }

    /** Version modules (`mod/platform/26.3`, `addons/cheat/26.2`) collapse to one, so their deliberate copies count once. */
    val versionlessPath: String get() = path.replace(VERSION_SEGMENT, "/*/")
}

class Copy(val path: String, val line: Int, val name: String)

class Duplicate(val body: String, val copies: List<Copy>)

/** Finds code written more than once across the repo, for `reuseReport`. Nothing here is listed by hand. */
object ReuseScan {
    private const val MIN_BODY = 120
    private const val MIN_LINE = 50

    /** Every `src/main/kotlin` file under `api`, `mod` and `addons`. */
    val sources: List<SourceFile> by lazy {
        val root = LangCatalog.repoDir("")
        listOf("api", "mod", "addons").map(root::resolve).filter(Files::isDirectory).flatMap { dir ->
            Files.walk(dir).use { paths ->
                paths.filter { it.extension == "kt" }
                    .map { root.relativize(it).invariantSeparatorsPathString }
                    .filter { "/src/main/kotlin/" in it && "/build/" !in it }
                    .toList()
            }
        }.map { SourceFile(it, root.resolve(it).readText()) }
    }

    /** Identical bodies of at least [MIN_BODY] normalized characters, in two or more files once versions collapse. */
    fun duplicates(files: List<SourceFile> = sources): List<Duplicate> {
        val versionless = files.associate { it.path to it.versionlessPath }
        return files.flatMap(::functions)
            .filter { (body, _) -> body.length >= MIN_BODY }
            .groupBy({ it.first }, { it.second })
            .filter { (_, copies) -> copies.map { versionless.getValue(it.path) }.distinct().size > 1 }
            .map { (body, copies) -> Duplicate(body, copies) }
    }

    /** Normalized code lines that recur across files, most frequent first: candidates for a helper. */
    fun repeatedLines(files: List<SourceFile> = sources, minCount: Int = 3): List<Pair<String, List<String>>> =
        files.groupBy { it.versionlessPath }.values.map { it.first() }
            .flatMap { file -> file.lines.map(::normalize).filter(::isRepeatCandidate).distinct().map { it to file.path } }
            .groupBy({ it.first }, { it.second })
            .filter { (_, paths) -> paths.size >= minCount }
            .toList()
            .sortedWith(compareByDescending<Pair<String, List<String>>> { it.second.size }.thenByDescending { it.first.length })

    private fun functions(file: SourceFile): List<Pair<String, Copy>> = FUNCTION.findAll(file.text).mapNotNull { match ->
        val body = body(file.text, match.range.last, indent = match.groupValues[1].length) ?: return@mapNotNull null
        val line = file.text.substring(0, match.range.first).count { it == '\n' } + 1
        normalize(body) to Copy(file.path, line, match.groupValues[2])
    }.toList()

    private fun body(text: String, openParen: Int, indent: Int): String? {
        val afterParams = closing(text, openParen, '(', ')') ?: return null
        var i = afterParams + 1
        while (i < text.length && text[i] != '{' && text[i] != '=' && text[i] != '\n') i++
        if (i >= text.length) return null
        return when (text[i]) {
            '{' -> closing(text, i, '{', '}')?.let { text.substring(i, it + 1) }
            '=' -> expressionBody(text, i + 1, indent)
            else -> null
        }
    }

    private fun expressionBody(text: String, start: Int, indent: Int): String {
        val lines = text.substring(start).lines()
        val taken = mutableListOf(lines.first())
        var depth = depth(lines.first())
        for (line in lines.drop(1)) {
            val continues = line.isNotBlank() && line.length - line.trimStart().length > indent
            if (depth <= 0 && !continues) break
            taken += line
            depth += depth(line)
        }
        return taken.joinToString("\n")
    }

    private fun depth(line: String) = line.count { it == '{' || it == '(' } - line.count { it == '}' || it == ')' }

    private fun closing(text: String, open: Int, opening: Char, closing: Char): Int? {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                opening -> depth++
                closing -> if (--depth == 0) return i
            }
        }
        return null
    }

    private fun normalize(code: String) = code.replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, "")
        .replace(WHITESPACE, " ").trim()

    private fun isRepeatCandidate(line: String): Boolean = line.length >= MIN_LINE &&
        NOT_STATEMENT.none { line.startsWith(it) }

    private val NOT_STATEMENT = listOf(
        "import ", "package ", "@", "//", "*", "/*", "override fun ", "fun ", "private fun ", "internal fun ", "class ", "object ",
    )
    private val FUNCTION = Regex("(?m)^([ \\t]*)(?:[\\w@]+(?:\\([^)\\n]*\\))?\\s+)*fun\\s+(?:<[^>]*>\\s*)?(?:\\S+\\.)?(\\w+)\\s*\\(")
    private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val LINE_COMMENT = Regex("(?m)^\\s*//.*$")
    private val WHITESPACE = Regex("\\s+")
}

private val VERSION_SEGMENT = Regex("/\\d+\\.\\d+(?:\\.\\d+)?/")
