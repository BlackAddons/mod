package org.blackaddons.api.feature

import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.util.I18n

/**
 * One ClickGUI sidebar tab. Declared in [Categories] or `Addon.categories`, never created implicitly.
 * ```
 * object Auras : FeatureCategory("auras", LucideIcons.combat) {
 *     val Combat = section("combat", "Combat")
 * }
 * ```
 * @param label fallback for `blackaddons.category.<id>`.
 * @param hideWhenEmpty hides the tab while it has no features (`Cheats` uses it for the legit edition).
 */
open class FeatureCategory(
    id: String,
    val icon: VectorIcon? = null,
    val label: String = id.replaceFirstChar(Char::uppercase),
    val hideWhenEmpty: Boolean = false,
) {
    /** Lowercase. Localization key, package segment and registry key. */
    val id: String = id.lowercase()

    private val declared = mutableListOf<FeatureSection>()

    /** Section headers only show once two or more hold features. */
    val sections: List<FeatureSection> get() = declared

    val defaultSection: FeatureSection? get() = declared.firstOrNull()

    /** Declaration bodies only. */
    protected fun section(id: String, label: String = id.replaceFirstChar(Char::uppercase)): FeatureSection =
        FeatureSection(this, id, label).also { declared += it }

    val translatedLabel: String get() = I18n.trOr("blackaddons.category.$id", label)

    override fun toString(): String = "FeatureCategory($id)"
}

/** A subcategory. Only [FeatureCategory.section] creates one, so it always knows its category. */
class FeatureSection internal constructor(
    val category: FeatureCategory,
    id: String,
    val label: String,
) {
    val id: String = id.lowercase()

    val translatedLabel: String get() = I18n.trOr("blackaddons.section.${category.id}.$id", label)

    override fun toString(): String = "FeatureSection(${category.id}/$id)"
}
