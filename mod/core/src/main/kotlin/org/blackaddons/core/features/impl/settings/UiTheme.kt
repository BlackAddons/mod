package org.blackaddons.core.features.impl.settings

import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.theme.BuiltInThemes
import org.blackaddons.core.theme.ThemeCatalog
import org.blackaddons.core.theme.ThemeDocument
import org.blackaddons.core.ui.screen.ThemeScreen

@AlwaysActive
object UiTheme : Feature(
    description = "Colors the BlackAddons interface. Pick one of ours, change it, or import one.",
    name = "Theme",
) {

    override val section = Categories.Settings.Appearance

    private val selectedId by string("Selected", BuiltInThemes.default.id).hidden()

    @Suppress("unused")
    private val configure by button("Theme", action = { UiManager.push(ThemeScreen()) }, label = "Open")
        .withDescription("Pick a theme, change its colors, or bring one in from the clipboard.")

    val menuBlur by boolean("Menu Blur", default = true)
        .withDescription("Blur the world behind BlackAddons menus. Off leaves it sharp.")

    internal val current: ThemeDocument get() = ThemeCatalog.byId(selectedId.value) ?: BuiltInThemes.default

    // Not init(), because the config isn't read yet at discovery.
    override fun onEnable() = refresh()

    internal fun select(document: ThemeDocument) {
        selectedId.value = document.id
        ThemeCatalog.apply(document)
        BlackaddonsConfig.save()
    }

    fun refresh() {
        ThemeCatalog.load()
        ThemeCatalog.apply(current)
    }
}
