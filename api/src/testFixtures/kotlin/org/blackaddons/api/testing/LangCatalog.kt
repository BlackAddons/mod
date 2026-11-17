package org.blackaddons.api.testing

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.util.I18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/** Lang checks for `lang/<namespace>/`. Features come from source, locales from the bundle: nothing listed by hand. */
class LangCatalog(private val namespace: String) {

    /** English names and describes every feature and has a key for every extension under [root]. */
    fun assertCoversFeaturesIn(root: Path) {
        val english = strings(ENGLISH)
        val declared = declaredFeatures(root)
        check(declared.isNotEmpty()) { "No features declared under $root" }
        for ((id, isExtension) in declared) {
            val prefix = "feature.${I18n.slug(id)}."
            if (isExtension) {
                assertTrue(english.keys.any { it.startsWith(prefix) }, "Missing catalog entry for extension $id")
            } else {
                assertTrue("${prefix}name" in english, "Missing name for $id")
                assertTrue("${prefix}desc" in english, "Missing description for $id")
            }
        }
    }

    /** Every locale has exactly the English keys, with matching placeholders. */
    fun assertLocalesMatchEnglish() {
        val english = strings(ENGLISH)
        val locales = shippedLocales() - ENGLISH
        check(locales.isNotEmpty()) { "No translations next to $ENGLISH in lang/$namespace" }
        for (locale in locales) {
            val translated = strings(locale)
            val missing = english.keys - translated.keys
            val extra = translated.keys - english.keys
            assertTrue(missing.isEmpty() && extra.isEmpty(), "$locale key coverage - missing: $missing, extra: $extra")
            english.forEach { (key, value) ->
                assertEquals(placeholders(value), placeholders(translated.getValue(key)), "$locale placeholders for $key")
            }
        }
    }

    private fun shippedLocales(): Set<String> {
        val url = requireNotNull(javaClass.classLoader.getResource("lang/$namespace/$ENGLISH.json")) {
            "Missing lang/$namespace/$ENGLISH.json on the test classpath"
        }
        check(url.protocol == "file") { "lang/$namespace must come from a directory in tests, got $url" }
        return Path.of(url.toURI()).parent.listDirectoryEntries("*.json").map { it.nameWithoutExtension }.toSet()
    }

    private fun strings(locale: String): Map<String, String> {
        val path = "lang/$namespace/$locale.json"
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing $path" }
        val root = stream.use { JsonParser.parseReader(it.reader()).asJsonObject }
        return buildMap { flatten("", root, this) }
    }

    private fun flatten(prefix: String, value: JsonObject, into: MutableMap<String, String>) {
        value.entrySet().forEach { (name, element) ->
            val key = if (prefix.isEmpty()) name else "$prefix.$name"
            when {
                element.isJsonObject -> flatten(key, element.asJsonObject, into)
                element.isJsonArray -> into[key] = element.asJsonArray.joinToString("\n", transform = JsonElement::getAsString)
                else -> into[key] = element.asString
            }
        }
    }

    private fun placeholders(value: String) = PLACEHOLDER.findAll(value).map { it.value }.toList()

    companion object {
        private const val ENGLISH = "en_us"

        private val PLACEHOLDER = Regex("%(?:\\d+\\$)?[a-zA-Z%]")
        private val FEATURE_PACKAGE = Regex("(?m)^package [\\w.]+\\.features\\.impl(?:\\.|$)")
        private val DECLARATION = Regex(
            "(?m)^\\s*(?:(?:public|internal|private)\\s+)?object\\s+([A-Za-z]\\w*)\\s*:\\s*(Feature|FeatureExtension)\\s*[({]",
        )
        private val ID_OVERRIDE = Regex("(?m)^\\s*override\\s+val\\s+id\\b")
        private val ID_LITERAL = Regex("(?m)^\\s*override\\s+val\\s+id(?:\\s*:\\s*String)?\\s*=\\s*\"([^\"\\\\]*)\"\\s*$")

        /** Resolved from the repo root (where `settings.gradle.kts` is). */
        fun repoDir(relative: String): Path {
            val here = Path.of("").toAbsolutePath()
            val root = generateSequence(here) { it.parent }.firstOrNull { it.resolve("settings.gradle.kts").isRegularFile() }
                ?: error("No settings.gradle.kts above $here")
            return root.resolve(relative)
        }

        /**
         * `object X : Feature(` / `: FeatureExtension(` in main `.features.impl` packages, by id (simple name
         * unless `id` is overridden with a literal).
         */
        private fun declaredFeatures(root: Path): Map<String, Boolean> = Files.walk(root).use { paths ->
            paths.filter { it.extension == "kt" && "/src/main/kotlin/" in it.invariantSeparatorsPathString }
                .toList()
                .flatMap { path ->
                    val source = path.readText()
                    if (!FEATURE_PACKAGE.containsMatchIn(source)) return@flatMap emptyList()
                    val declarations = DECLARATION.findAll(source).toList()
                    val idOverride = ID_LITERAL.find(source)?.groupValues?.get(1)
                    check(idOverride != null || !ID_OVERRIDE.containsMatchIn(source)) {
                        "Cannot statically resolve the id override in $path"
                    }
                    check(idOverride == null || declarations.size == 1) {
                        "A file overriding a feature id must declare exactly one feature: $path"
                    }
                    declarations.map { (idOverride ?: it.groupValues[1]) to (it.groupValues[2] == "FeatureExtension") }
                }
                .toMap()
        }
    }
}
