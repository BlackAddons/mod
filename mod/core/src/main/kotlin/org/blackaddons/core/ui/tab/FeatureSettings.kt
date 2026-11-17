package org.blackaddons.core.ui.tab

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.feature.translatedSettingGroup
import org.blackaddons.api.ui.widget.setting.SettingWidget
import org.blackaddons.api.ui.widget.setting.widgetFor
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.core.log.Log

/**
 * A feature's settings for display: visible widgets, aligned in columns, split into ungrouped and named groups. Also
 * answers search. Pure model, so it unit-tests alone.
 */
internal class FeatureSettings(private val feature: Feature) {

    private val allWidgets: List<SettingWidget> by lazy {
        feature.configSettings.mapNotNull { setting ->
            widgetFor(setting).also {
                if (it == null) Log.main.warn("No widget for setting '{}' ({}) of feature '{}' - not shown", setting.id, setting::class.java.name, feature.id)
            }
        }
    }

    /** Calculated each call, because [SettingWidget.naturalWidth] changes with language, rebinds and list sizes. */
    fun visible(): List<SettingWidget> =
        allWidgets.filter { it.setting.isVisible }.also(::alignColumns)

    fun ungrouped(): List<SettingWidget> = visible().filter { it.setting.group == null }

    fun groups(): List<Pair<String, List<SettingWidget>>> {
        val byGroup = LinkedHashMap<String, MutableList<SettingWidget>>()
        for (w in visible()) {
            val g = w.setting.group ?: continue
            byGroup.getOrPut(g) { mutableListOf() }.add(w)
        }
        return byGroup.map { it.key to it.value }.sortedBy { it.first == "Debug" }
    }

    fun forceOpen(query: String?): Boolean {
        val q = activeQuery(query) ?: return false
        return visible().any { matches(it, q) }
    }

    fun groupForceOpen(group: String, query: String?): Boolean {
        val q = activeQuery(query) ?: return false
        val ws = groups().firstOrNull { it.first == group }?.second ?: return false
        return ws.any { matches(it, q) }
    }

    /**
     * One shared width for all stretching widgets across the whole feature, so edges line up. Widgets whose size means
     * something (toggles) opt out through [SettingWidget.stretches].
     */
    private fun alignColumns(widgets: List<SettingWidget>) {
        val column = widgets.filter { it.stretches }
        if (column.isEmpty()) return
        val columnWidth = column.maxOf { it.naturalWidth }
        column.forEach { it.widgetWidth = columnWidth }
    }

    private fun activeQuery(query: String?): String? =
        query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun matches(w: SettingWidget, q: String): Boolean =
        w.setting.translatedName.containsIgnoreCase(q) ||
            w.setting.group?.let(::translatedSettingGroup)?.containsIgnoreCase(q) == true ||
            w.setting.name.containsIgnoreCase(q)
}
