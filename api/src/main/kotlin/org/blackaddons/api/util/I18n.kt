package org.blackaddons.api.util

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.log.Log
import org.blackaddons.api.util.I18n.trOr
import kotlin.math.abs

/**
 * Our own localization (not Minecraft's, which can't see lang files in modules without a `fabric.mod.json`).
 * Bundles are nested JSON at `lang/<namespace>/<locale>.json`, flattened to dotted keys.
 * Keys:
 * - `blackaddons.feature.<feature_id>.name` / `.desc`
 * - `blackaddons.feature.<feature_id>.setting.<setting_id>.name` / `.desc` / `.label` / `.unsafe`
 * - `blackaddons.category.<id>`, `blackaddons.section.<category_id>.<section_id>`, `blackaddons.tab.<id>`
 * Missing translations fall back to the caller's string ([trOr]).
 */
object I18n {

    /** Shipped by every bundle; fallback for missing keys. */
    const val FALLBACK_LOCALE = "en_us"

    /**
     * Every bundle flattens under this, whatever its namespace - the namespace names the file, not the key.
     */
    private const val KEY_ROOT = "blackaddons"

    private val namespaces = LinkedHashSet<String>()

    @Volatile private var strings: Map<String, String> = emptyMap()
    @Volatile private var fallback: Map<String, String> = emptyMap()
    @Volatile private var loadedLocale: String? = null

    /** Adds a `lang/<namespace>/` directory to read. A namespace with no bundle is fine. */
    @Synchronized
    fun register(namespace: String) {
        if (!namespaces.add(namespace)) return
        loadedLocale = null
    }

    fun tr(key: String): String = lookup(key) ?: key

    fun tr(key: String, vararg args: Any): String = format(lookup(key), args) ?: key

    fun trOr(key: String, fallback: String): String = lookup(key) ?: fallback

    fun trOr(key: String, fallback: String, vararg args: Any): String =
        format(lookup(key) ?: fallback, args) ?: fallback

    /** `tr` for every key under [prefix]. */
    fun scope(prefix: String): LangScope = LangScope(prefix)

    /**
     * Localized plural of [count].
     * Russian: [one] 1, 21, 31...; [few] 2..4, 22..24...; [many] 0, 5..20, 25..30...
     * Other locales: [one] for 1, [many] otherwise.
     */
    fun plural(
        count: Int,
        one: String,
        few: String = one,
        many: String = few,
    ): String {
        val loc = runCatching { Platform.locale.current }.getOrDefault(FALLBACK_LOCALE).lowercase()
        val abs = abs(count)
        val word = when {
            loc.startsWith("ru") -> {
                val mod10 = abs % 10
                val mod100 = abs % 100
                when (mod10) {
                    1 if mod100 != 11 -> one
                    in 2..4 if mod100 !in 12..14 -> few
                    else -> many
                }
            }
            else -> if (count == 1) one else many
        }
        return "$count $word"
    }

    /** e.g. "1 step", "2 шага", "5 шагов". */
    fun steps(count: Int): String {
        val loc = runCatching { Platform.locale.current }.getOrDefault(FALLBACK_LOCALE).lowercase()
        return when {
            loc.startsWith("ru") -> plural(count, "шаг", "шага", "шагов")
            loc.startsWith("es") -> plural(count, "paso", "pasos", "pasos")
            else -> plural(count, "step", "steps", "steps")
        }
    }

    /** Lang-key-safe slug. */
    fun slug(s: String): String = s.lowercase().replace(NON_KEY_CHARS, "_").trim('_')

    // Hoisted: [slug] is on the render path.
    private val NON_KEY_CHARS = Regex("[^a-z0-9_]+")

    private fun format(value: String?, args: Array<out Any>): String? {
        if (value == null) return null
        if (args.isEmpty()) return value
        // A bad lang-file format string shouldn't kill the frame.
        return runCatching { value.format(*args) }.getOrElse { value }
    }

    private fun lookup(key: String): String? {
        ensureLoaded()
        return strings[key] ?: fallback[key]
    }

    /** Polled per lookup (a volatile read and a compare) rather than hooked, so it can't go stale. */
    private fun ensureLoaded() {
        val wanted = runCatching { Platform.locale.current }.getOrDefault(FALLBACK_LOCALE)
        if (wanted == loadedLocale) return
        reload(wanted)
    }

    @Synchronized
    private fun reload(locale: String) {
        if (locale == loadedLocale) return
        fallback = merge(FALLBACK_LOCALE)
        strings = if (locale == FALLBACK_LOCALE) fallback else merge(locale)
        loadedLocale = locale
        Log.main.debug("Loaded {} strings for locale {}", strings.size, locale)
    }

    /** Key collisions between bundles are reported instead of silently last-wins. */
    private fun merge(locale: String): Map<String, String> {
        val merged = HashMap<String, String>()
        for (namespace in namespaces) {
            val bundle = read(namespace, locale) ?: continue
            for ((key, value) in bundle) {
                val previous = merged.put(key, value)
                if (previous != null && previous != value) {
                    Log.main.warn("Lang key '{}' is defined more than once; '{}' won", key, namespace)
                }
            }
        }
        return merged
    }

    private fun read(namespace: String, locale: String): Map<String, String>? {
        val path = "/lang/$namespace/$locale.json"
        val stream = I18n::class.java.getResourceAsStream(path) ?: return null
        return runCatching {
            val root = stream.use { JsonParser.parseReader(it.reader()) }
            val flat = HashMap<String, String>()
            flatten(KEY_ROOT, root.asJsonObject, flat)
            flat
        }.onFailure { Log.main.error("Failed to read lang bundle {}", path, it) }.getOrNull()
    }

    /** String arrays join with newlines, for multi-line descriptions. */
    private fun flatten(prefix: String, obj: JsonObject, into: MutableMap<String, String>) {
        for ((name, value) in obj.entrySet()) {
            val key = "$prefix.$name"
            when {
                value.isJsonObject -> flatten(key, value.asJsonObject, into)
                value.isJsonArray -> into[key] = value.asJsonArray.joinToString("\n", transform = JsonElement::getAsString)
                value.isJsonPrimitive -> into[key] = value.asString
                else -> Log.main.warn("Lang key '{}' is neither text, a list of lines, nor a group", key)
            }
        }
    }
}
