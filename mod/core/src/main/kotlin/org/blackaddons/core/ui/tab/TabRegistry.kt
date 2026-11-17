package org.blackaddons.core.ui.tab

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.CategoryRegistry
import org.blackaddons.api.feature.FeatureCategory
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.util.I18n
import org.blackaddons.core.cosmetics.store.CosmeticStore
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.ui.screen.HudEditorScreen
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.chat.ChatScreen
import org.blackaddons.core.ui.screen.cosmetic.CosmeticEditorScreen
import org.blackaddons.core.ui.screen.screenshots.ScreenshotsScreen

/**
 * The ClickGUI sidebar in draw order: our categories, a rule, non-feature screens, one group per addon (with its name
 * as header), a rule, then Dev. The order lives here, not in the `FeatureCategory` declaration order. [Categories]
 * decides what a category is.
 */
internal object TabRegistry {

    fun buildTabs(): List<Tab> = trimDividers(
        buildList {
            category(Categories.Dungeons)
            category(Categories.Visual)
            category(Categories.Interface)
            category(Categories.Chat)
            category(Categories.Client)
            category(Categories.Cheats)
            category(Categories.Settings)

            add(DividerTab)
            add(ContentTab(I18n.tr("blackaddons.tab.configs"), ConfigsTabController(), LucideIcons.configs))
            // Without it, the window could only be opened with a command or through its echo's prefix in the game chat.
            add(TriggerTab(I18n.tr("blackaddons.tab.chat"), LucideIcons.messages) { ChatScreen.open() })
            add(TriggerTab(I18n.tr("blackaddons.tab.actions"), LucideIcons.actions) { ActionEditors.open() })
            add(TriggerTab(I18n.tr("blackaddons.tab.hud_editor"), LucideIcons.hudEditor) { HudEditorScreen.open() })
            add(TriggerTab(I18n.tr("blackaddons.tab.screenshots"), LucideIcons.image) { ScreenshotsScreen.open() })
            // Owners only: if the cached public catalog lists the player, /v1/me/cosmetic resolves. Rebuilt each open.
            if (CosmeticStore.lookup(Platform.player.uuid) != null) {
                add(
                    TriggerTab(I18n.tr("blackaddons.tab.cosmetic_editor"), LucideIcons.cosmeticEditor) {
                        CosmeticEditorScreen.open()
                    },
                )
            }
            add(ContentTab(I18n.tr("blackaddons.tab.about"), AboutTabController(), LucideIcons.about))

            // No rule here, because each addon header draws its own.
            addonGroups().forEach { (addonName, categories) ->
                add(HeaderTab(addonName))
                categories.forEach { category(it) }
            }

            add(DividerTab)
            category(Categories.Dev)
        },
    )

    private fun MutableList<Tab>.category(category: FeatureCategory) {
        if (category.hideWhenEmpty && FeatureManager.byCategory(category).isEmpty()) return
        // translatedLabel, because addon categories have no `blackaddons.category.<id>` key.
        add(ContentTab(category.translatedLabel, FeatureCategoryTabController(category), category.icon))
    }

    internal fun trimDividers(rows: List<Tab>): List<Tab> {
        val out = mutableListOf<Tab>()
        for (row in rows) {
            if (row is DividerTab && (out.isEmpty() || out.last() is DividerTab)) continue
            out += row
        }
        while (out.lastOrNull() is DividerTab) out.removeAt(out.lastIndex)
        return out
    }

    /**
     * Addon name -> its categories, sorted by name (load order changes). Addons with only host categories get no
     * header.
     */
    private fun addonGroups(): Map<String, List<FeatureCategory>> =
        CategoryRegistry.all
            .mapNotNull { category ->
                val addonId = CategoryRegistry.addonIdOf(category) ?: return@mapNotNull null
                if (FeatureManager.byCategory(category).isEmpty()) null else addonDisplayName(addonId) to category
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()

    /** `blackaddons.addon.<id>` if localized, otherwise "my-addon" -> "My Addon". */
    private fun addonDisplayName(addonId: String): String {
        val pretty = addonId.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        return I18n.trOr("blackaddons.addon.$addonId", pretty.ifBlank { addonId })
    }
}
