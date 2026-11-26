package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.feature.settings.impl.*

/** Null for types it can't draw (e.g. an addon's own [Setting]); the caller skips those. */
fun widgetFor(setting: Setting<*>): SettingWidget? = when (setting) {
    is BooleanSetting -> BooleanWidget(setting)
    is NumberSetting -> NumberWidget(setting)
    is ModeSetting -> if (setting.isDropdown) ModeDropdownWidget(setting) else ModeWidget(setting)
    is ColorSetting -> ColorWidget(setting)
    is KeybindSetting -> KeybindWidget(setting)
    is ButtonSetting -> ButtonWidget(setting)
    is StringSetting -> StringWidget(setting)
    is ListSetting -> ListWidget(setting)
    else -> null
}
