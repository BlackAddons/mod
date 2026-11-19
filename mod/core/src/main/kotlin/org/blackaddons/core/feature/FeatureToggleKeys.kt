package org.blackaddons.core.feature

import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.input.KeyInputEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.matches
import org.blackaddons.api.feature.settings.impl.KeybindSetting
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n

/** Every feature's [Feature.toggleBind], one listener for all of them. */
internal object FeatureToggleKeys {
    private val tr = I18n.scope("blackaddons.feature.menu")

    fun init() {
        EventBus.subscribe<KeyInputEvent> { if (!event.isRepeat) press { it.matches(event) } }
        EventBus.subscribe<MouseClickEvent> { press { it.matches(event) } }
    }

    private inline fun press(matches: (KeybindSetting) -> Boolean) {
        if (Platform.screenHost.anyScreenOpen) return
        FeatureManager.features.filter { !it.isAlwaysActive && matches(it.toggleBind) }.forEach { feature ->
            feature.toggle()
            val name = feature.translatedName
            Toasts.show(if (feature.enabled) tr("toggled_on", "%s enabled", name) else tr("toggled_off", "%s disabled", name))
        }
    }
}
