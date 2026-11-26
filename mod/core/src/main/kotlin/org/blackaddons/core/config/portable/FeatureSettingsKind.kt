package org.blackaddons.core.config.portable

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.blackaddons.api.config.Savable
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.feature.settings.impl.*
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.feature.translatedOption
import org.blackaddons.api.feature.translatedSettingGroup
import org.blackaddons.api.util.color.toHex
import org.blackaddons.api.util.formatTrimmed
import org.blackaddons.api.util.io.Portable
import org.blackaddons.api.util.io.child
import org.blackaddons.api.util.io.str
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.feature.FeatureManager

/**
 * One feature's settings:
 * ```json
 * { "feature": "DungeonMap", "addon": "blackaddons-cheat", "settings": { "Hide In Boss": true } }
 * ```
 * Masked (secret) fields never leave and are never overwritten. [target] limits the import to one feature.
 */
internal class FeatureSettingsKind(private val target: Feature? = null) : PortableKind<Feature>(KIND) {

    override val title: String get() = tr("title", "Paste settings")
    override val noun: String get() = tr("noun", "feature settings")

    override fun preview(json: JsonObject): ImportPreview<Feature> {
        val id = json.str("feature") ?: return ImportPreview.Failed(tr("err_no_feature", "That string names no feature."))
        val addon = json.str("addon")
        val feature = if (target != null) {
            target.takeIf { it.id == id }
                ?: return ImportPreview.Failed(tr("err_other", "Those settings belong to %s.", nameOf(id, addon)))
        } else {
            FeatureManager.featureById(id, addon)
                ?: return ImportPreview.Failed(tr("err_missing", "This game has no feature \"%s\".", id))
        }

        val staged = staged(feature, json.child("settings"))
        val rows = rows(staged)
        return ImportPreview.Ready(
            headline = tr("headline", "Settings for %s", feature.translatedName),
            note = if (rows.isEmpty()) tr("same", "These are the settings you already have.") else tr("changed", "%d settings change:", rows.size),
            rows = rows,
            canApply = rows.isNotEmpty(),
        ) {
            staged.forEach { (setting, value) -> (setting as Savable).read(value) }
            feature
        }
    }

    override fun afterImport(value: Feature) = BlackaddonsConfig.save()

    private fun nameOf(id: String, addon: String?): String =
        FeatureManager.featureById(id, addon)?.translatedName ?: id

    companion object {
        val tr = PortableImports.tr.scope("feature")

        const val KIND = "feature"
        private const val EMPTY = "-"

        fun hasTransferable(feature: Feature): Boolean = feature.configSettings.any(::transferable)

        fun export(feature: Feature): String = Portable.encode(KIND, JsonObject().apply {
            addProperty("feature", feature.id)
            FeatureManager.addonIdOf(feature)?.let { addProperty("addon", it) }
            add("settings", JsonObject().apply {
                feature.configSettings.filter(::transferable).forEach { add(it.id, (it as Savable).write()) }
            })
        })

        fun transferable(setting: Setting<*>): Boolean =
            setting is Savable && !(setting is StringSetting && setting.isMasked)

        fun staged(feature: Feature, settings: JsonObject?): List<Pair<Setting<*>, JsonElement>> =
            settings?.entrySet().orEmpty().mapNotNull { (settingId, value) ->
                feature.getSettingById(settingId)?.takeIf(::transferable)?.let { it to value }
            }

        fun rows(staged: List<Pair<Setting<*>, JsonElement>>, prefix: String? = null): List<DiffRow> =
            staged.mapNotNull { (setting, value) -> diff(setting, value, prefix) }

        /** Computed on the live setting (set, read back, restore), so clamping and snapping match the real import. */
        private fun diff(setting: Setting<*>, incoming: JsonElement, prefix: String?): DiffRow? {
            val savable = setting as Savable
            val saved = savable.write()
            val before = display(setting)
            savable.read(incoming)
            val changed = savable.write() != saved
            val after = display(setting)
            savable.read(saved)
            if (!changed) return null
            val label = setting.group?.let { "${translatedSettingGroup(it)} / ${setting.translatedName}" } ?: setting.translatedName
            return DiffRow(prefix?.let { "$it / $label" } ?: label, before, after)
        }

        fun onOff(on: Boolean) = DiffValue(if (on) tr("on", "On") else tr("off", "Off"))

        private fun display(setting: Setting<*>): DiffValue = when (setting) {
            is BooleanSetting -> onOff(setting.value)
            is NumberSetting -> DiffValue(setting.value.formatTrimmed() + setting.unit)
            is ColorSetting -> DiffValue(
                setting.baseColor.toHex(setting.allowAlpha) + if (setting.chroma) " ${tr("chroma", "chroma")}" else "",
                setting.baseColor,
            )
            is KeybindSetting -> DiffValue(setting.displayName)
            is ListSetting -> DiffValue(setting.value.joinToString(", ").ifEmpty { EMPTY })
            is ModeSetting -> DiffValue(setting.translatedOption(setting.value))
            else -> DiffValue(setting.value?.toString()?.ifEmpty { EMPTY } ?: EMPTY)
        }
    }
}
