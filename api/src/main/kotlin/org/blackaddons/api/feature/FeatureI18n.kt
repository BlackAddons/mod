package org.blackaddons.api.feature

import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.feature.settings.impl.ButtonSetting
import org.blackaddons.api.feature.settings.impl.ListSetting
import org.blackaddons.api.feature.settings.impl.ModeSetting
import org.blackaddons.api.feature.settings.impl.OrderSetting
import org.blackaddons.api.feature.settings.impl.StringSetting
import org.blackaddons.api.hud.HudElement
import org.blackaddons.api.util.I18n

/**
 * Localized views of features and settings. Keys: `blackaddons.feature.<feature>.name`, settings under their
 * declarer: `blackaddons.feature.<declarer>.setting.<setting>...`. Everything falls back to the raw text.
 */

val Feature.translatedName: String
    get() = I18n.trOr("blackaddons.feature.${I18n.slug(id)}.name", name)

val HudElement.translatedName: String
    get() = I18n.trOr("blackaddons.hud.${I18n.slug(name)}", name)

val Feature.translatedDescription: String?
    get() {
        val raw = description ?: return null
        return I18n.trOr("blackaddons.feature.${I18n.slug(id)}.desc", raw)
    }

/** Keyed under the declaring feature or extension, which may differ from the feature showing it. */
val Setting<*>.translatedName: String
    get() = I18n.trOr("${settingKey()}.name", name)

val ButtonSetting.translatedLabel: String
    get() = I18n.trOr("${settingKey()}.label", label)

/** Why the current value is risky, or null. */
val Setting<*>.translatedUnsafeReason: String?
    get() {
        val raw = unsafeReason ?: return null
        return I18n.trOr("${settingKey()}.unsafe", raw)
    }

val Setting<*>.translatedDescription: String?
    get() {
        val raw = description ?: return null
        return I18n.trOr("${settingKey()}.desc", raw)
    }

fun FeatureLabel.translatedReason(declarerId: String): String? {
    val raw = reason ?: return null
    return I18n.trOr("blackaddons.feature.${I18n.slug(declarerId)}.label.${kind.name.lowercase()}", raw)
}

/** The raw [group] stays the UI-state key. */
fun translatedSettingGroup(group: String): String =
    I18n.trOr("blackaddons.setting_group.${I18n.slug(group)}", group)

/** The stored value itself is never translated. */
fun ModeSetting.translatedOption(value: String): String =
    I18n.trOr("${settingKey()}.option.${I18n.slug(value)}", value)

/** Same keys as a mode's options. */
fun OrderSetting.translatedOption(value: String): String =
    I18n.trOr("${settingKey()}.option.${I18n.slug(value)}", value)

/** An [org.blackaddons.api.feature.settings.impl.EntryOption] choice, under the same keys as a mode's options. */
fun ListSetting.translatedOption(value: String): String =
    I18n.trOr("${settingKey()}.option.${I18n.slug(value)}", value)

val StringSetting.translatedPlaceholder: String
    get() = I18n.trOr("${settingKey()}.placeholder", placeholder)

val ListSetting.translatedPlaceholder: String
    get() = I18n.trOr("${settingKey()}.placeholder", placeholder)

/** Keyed under the declarer: `blackaddons.feature.<declarer>.action.<id>.label`. */
val MenuAction.translatedLabel: String
    get() = I18n.trOr("${actionKey()}.label", label)

/** Null when the action doesn't confirm. */
val MenuAction.translatedConfirm: String?
    get() {
        val raw = confirm ?: return null
        return I18n.trOr("${actionKey()}.confirm", raw)
    }

private fun MenuAction.actionKey(): String =
    "blackaddons.feature.${I18n.slug(namespace)}.action.${I18n.slug(id)}"

private fun Setting<*>.settingKey(): String {
    val declarer = namespace ?: owner?.id ?: return ""
    return "blackaddons.feature.${I18n.slug(declarer)}.setting.${I18n.slug(id)}"
}
