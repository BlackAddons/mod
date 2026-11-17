package org.blackaddons.core.feature

import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.api.feature.FeatureLabel
import org.blackaddons.api.feature.translatedReason
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.ServedDocument
import org.blackaddons.core.update.ModVersion

/**
 * Declared labels plus those from the served `feature_labels` document. It can add or replace labels per mod
 * version without a release:
 * ```json
 * { "features": [
 *   { "id": "AutoRelic", "from": "0.1.4-beta", "until": "0.1.5-beta",
 *     "labels": [ { "kind": "untested", "reason": "The input-cancel fix has not been run in game yet." } ] }
 * ] }
 * ```
 * `from` is inclusive, `until` exclusive, both optional. A served label replaces a declared one of the same kind.
 */
object FeatureLabels {

    private const val KEY = "feature_labels"

    /** The reason is already localized. [extension] is set when the label isn't the feature's own. */
    data class Shown(val label: FeatureLabel, val reason: String?, val extension: FeatureExtension?)

    @Volatile
    private var served: Map<String, List<FeatureLabel>> = emptyMap()

    fun load() = ServedDocument.load("${Backend.GAMEDATA_URL}/$KEY", "gamedata/$KEY") { document ->
        served = parse(document, ModVersion.parse(Platform.blackaddonsVersion))
        Log.main.debug("Feature labels loaded from game data: {} overrides", served.size)
    }

    fun of(feature: Feature): List<Shown> = resolve(feature.id, feature.labels, null)

    fun of(extension: FeatureExtension): List<Shown> = resolve(extension.id, extension.labels, extension)

    fun onRow(feature: Feature): List<Shown> =
        of(feature) + FeatureManager.extensionsOf(feature).flatMap(::of)

    private fun resolve(id: String, declared: List<FeatureLabel>, extension: FeatureExtension?): List<Shown> {
        val fromBackend = served[id].orEmpty()
        val kept = declared.filter { label -> fromBackend.none { it.kind == label.kind } }
        return kept.map { Shown(it, it.translatedReason(id), extension) } + fromBackend.map { Shown(it, it.reason, extension) }
    }

    internal fun parse(document: JsonObject, version: ModVersion?): Map<String, List<FeatureLabel>> {
        val result = HashMap<String, List<FeatureLabel>>()
        val entries = document.getAsJsonArray("features") ?: return result
        for (element in entries) {
            val entry = element as? JsonObject ?: continue
            val id = entry.get("id")?.asString ?: continue
            if (!applies(entry, version)) continue
            val labels = entry.getAsJsonArray("labels")?.mapNotNull { label(it as? JsonObject) }.orEmpty()
            result[id] = result[id].orEmpty().filter { old -> labels.none { it.kind == old.kind } } + labels
        }
        return result
    }

    // Unparseable versions (dev builds) only match entries without a range.
    private fun applies(entry: JsonObject, version: ModVersion?): Boolean {
        val from = entry.get("from")?.asString?.let(ModVersion::parse)
        val until = entry.get("until")?.asString?.let(ModVersion::parse)
        if (from == null && until == null) return true
        if (version == null) return false
        return (from == null || version >= from) && (until == null || version < until)
    }

    private fun label(json: JsonObject?): FeatureLabel? {
        val kind = json?.get("kind")?.asString?.uppercase() ?: return null
        val parsed = FeatureLabel.Kind.entries.firstOrNull { it.name == kind } ?: return null
        return FeatureLabel(parsed, json.get("reason")?.asString?.takeIf { it.isNotBlank() })
    }
}
