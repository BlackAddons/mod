package org.blackaddons.core.config.portable

import com.google.gson.JsonObject
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.util.formatTrimmed
import org.blackaddons.api.util.io.*
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.feature.FeatureManager

internal object ConfigPortable : PortableKind<Unit>("config") {
    private val tr = PortableImports.tr.scope("config")

    override val title: String get() = tr("title", "Import config")
    override val noun: String get() = tr("noun", "a whole config")

    fun export(): String = Portable.encode(kind, BlackaddonsConfig.shareable())

    override fun preview(json: JsonObject): ImportPreview<Unit> {
        val features = json.array("features") ?: return ImportPreview.Failed(tr("err_read", "That config could not be read."))
        var missing = 0
        val rows = buildList {
            features.forEach { element ->
                val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val feature = entry.str("id")?.let { FeatureManager.featureById(it, entry.str("addon")) }
                    ?: return@forEach run { missing++ }
                val name = feature.translatedName
                if (!feature.isAlwaysActive && entry.has("enabled") && entry.bool("enabled") != feature.enabled) {
                    add(DiffRow("$name / ${tr("enabled", "Enabled")}", FeatureSettingsKind.onOff(feature.enabled), FeatureSettingsKind.onOff(!feature.enabled)))
                }
                addAll(FeatureSettingsKind.rows(FeatureSettingsKind.staged(feature, entry.child("settings")), name))
            }
            json.array("huds")?.forEach { element ->
                val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val hud = entry.str("name")?.let { FeatureManager.hudByName(it, entry.str("addon")) } ?: return@forEach
                val before = placement(hud.anchorX.name, hud.anchorY.name, hud.offsetX, hud.offsetY, hud.scaleX, hud.scaleY, hud.fontOverride)
                val after = placement(
                    entry.str("anchorX") ?: hud.anchorX.name, entry.str("anchorY") ?: hud.anchorY.name,
                    entry.float("offsetX", hud.offsetX), entry.float("offsetY", hud.offsetY),
                    entry.float("scaleX", hud.scaleX), entry.float("scaleY", hud.scaleY),
                    entry.str("font") ?: hud.fontOverride,
                )
                if (before != after) add(DiffRow("HUD / ${hud.translatedName}", DiffValue(before), DiffValue(after)))
            }
        }
        val changes = if (rows.isEmpty()) tr("same", "Identical to your config.") else tr("changed", "%d changes:", rows.size)
        return ImportPreview.Ready(
            headline = tr("headline", "A shared config"),
            note = if (missing == 0) changes else "${tr("missing", "%d features this game does not have are skipped.", missing)} $changes",
            rows = rows,
            canApply = rows.isNotEmpty(),
        ) { BlackaddonsConfig.importShared(json) }
    }

    private fun placement(anchorX: String, anchorY: String, x: Float, y: Float, scaleX: Float, scaleY: Float, font: String) =
        "$anchorX/$anchorY (${x.formatTrimmed(0)}, ${y.formatTrimmed(0)}) · ${scaleX.formatTrimmed()}×${scaleY.formatTrimmed()} · $font"
}
