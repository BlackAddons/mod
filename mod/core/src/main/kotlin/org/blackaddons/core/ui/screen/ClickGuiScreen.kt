package org.blackaddons.core.ui.screen

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.FeatureSection
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.ui.sidebar.Sidebar
import org.blackaddons.core.ui.tab.ContentTab
import org.blackaddons.core.ui.tab.DividerTab
import org.blackaddons.core.ui.tab.FeatureCategoryTabController
import org.blackaddons.core.ui.tab.HeaderTab
import org.blackaddons.core.ui.tab.Tab
import org.blackaddons.core.ui.tab.TabRegistry
import org.blackaddons.core.ui.tab.TriggerTab

class ClickGuiScreen(private var focus: FeatureSection? = null) : Panel() {

    private val tabs = mutableListOf<Tab>()
    private lateinit var sidebar: Sidebar

    private val searchField = widgets.add(TextInput(placeholder = I18n.tr("blackaddons.search.placeholder")))

    // Gets the pointer. Keys reach it with or without focus (see keyPressed).
    private val content = widgets.add(object : InputTarget {
        override fun mouseClicked(mouse: MouseButtonInput) =
            currentController()?.mouseClicked(mouse) == true
        override fun mouseReleased(mouse: MouseButtonInput) =
            currentController()?.mouseReleased(mouse) == true
        override fun mouseDragged(drag: MouseDragInput) =
            currentController()?.mouseDragged(drag) == true
        override fun mouseScrolled(wheel: MouseScrollInput) =
            currentController()?.mouseScrolled(wheel) == true
    })

    init {
        keys.on(Keys.F, ctrl = true) { widgets.focus(searchField) }
    }
    private val searchResults = FeatureCategoryTabController(
        featuresProvider = ::searchMatches,
        showCategoryBadge = true,
        emptyMessageKey = "blackaddons.search.no_match",
        highlightQuery = { searchField.text },
    )

    // Polled on each render and input walk, so cached per query.
    private var lastQuery: String? = null
    private var lastMatches: List<Feature> = emptyList()

    private fun searchMatches(): List<Feature> {
        val q = searchField.text.trim().lowercase()
        if (q != lastQuery) {
            lastQuery = q
            lastMatches = if (q.isEmpty()) emptyList() else matchingFeatures(q)
        }
        return lastMatches
    }

    private fun matchingFeatures(q: String): List<Feature> = FeatureManager.features
        .filter { f ->
            f.translatedName.containsIgnoreCase(q) ||
                f.name.containsIgnoreCase(q) ||
                // Also matches setting names ("mana" finds Stat Bars).
                f.configSettings.any { s ->
                    !s.isHidden && (s.translatedName.containsIgnoreCase(q) || s.name.containsIgnoreCase(q))
                }
        }
        .sortedBy { it.translatedName }

    private var containerX = 0f
    private var containerY = 0f
    private var containerW = 0f
    private var containerH = 0f

    private var searchX = 0f
    private var searchY = 0f
    private var searchW = 0f
    private var searchH = 0f

    private val isSearching: Boolean get() = searchField.text.isNotEmpty()

    override fun onShow() {
        // Translated names feed it, and the language might have changed.
        lastQuery = null
        tabs.clear()
        tabs.addAll(TabRegistry.buildTabs())

        if (::sidebar.isInitialized) widgets.remove(sidebar)
        sidebar = widgets.add(Sidebar(tabs) { index ->
            when (val tab = tabs[index]) {
                is ContentTab -> sidebar.selectedIndex = index
                is TriggerTab -> tab.onClick()
                is HeaderTab, is DividerTab -> Unit
            }
        })
        sidebar.selectedIndex = tabs.indexOfFirst { it is ContentTab }.coerceAtLeast(0)
        focus?.let(::showSection)
        // Only once, so coming back from another screen keeps whatever the player opened since.
        focus = null
    }

    private fun showSection(section: FeatureSection) {
        val index = tabs.indexOfFirst { it.categoryController()?.category == section.category }
        if (index < 0) return
        sidebar.selectedIndex = index
        tabs[index].categoryController()?.focus(section)
    }

    private fun Tab.categoryController() = (this as? ContentTab)?.controller as? FeatureCategoryTabController

    private fun currentController() =
        if (isSearching) searchResults else (tabs[sidebar.selectedIndex] as? ContentTab)?.controller

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {

        val w = UiManager.screenWidth
        val h = UiManager.screenHeight

        Render.rect(0f, 0f, w, h, Theme.OVERLAY_DIM)

        val maxW = (w - EDGE_PAD * 2f).coerceAtLeast(0f)
        val maxH = (h - EDGE_PAD * 2f).coerceAtLeast(0f)
        containerW = minOf(maxOf(w * SCREEN_FRACTION, MIN_WIDTH), maxW)
        containerH = minOf(maxOf(h * SCREEN_FRACTION, MIN_HEIGHT), maxH)
        containerX = (w - containerW) / 2f
        containerY = (h - containerH) / 2f

        Render.rect(containerX, containerY, containerW, containerH, Theme.BACKGROUND, Theme.RADIUS_LARGE)

        sidebar.render(containerX, containerY, sidebar.width, containerH, mouseX, mouseY)

        val contentX = containerX + sidebar.width
        val contentY = containerY
        val contentW = containerW - sidebar.width
        val contentH = containerH

        val title = if (isSearching) I18n.tr("blackaddons.search.title") else I18n.tr("blackaddons.title")
        Render.text(
            title,
            contentX + Theme.PADDING_LARGE,
            contentY + Theme.PADDING_LARGE,
            TextStyle.TITLE_PRIMARY_BOLD,
        )

        searchW = (contentW * 0.45f).coerceIn(140f, 220f)
        searchH = 20f
        searchX = contentX + contentW - searchW - Theme.PADDING_LARGE
        searchY = contentY + Theme.PADDING_LARGE - 2f
        searchField.render(searchX, searchY, searchW, searchH, mouseX, mouseY)

        val headerHeight = Theme.PADDING_LARGE + Theme.FONT_TITLE + Theme.PADDING
        val controller = currentController() ?: return
        controller.render(
            contentX,
            contentY + headerHeight,
            contentW,
            contentH - headerHeight,
            mouseX,
            mouseY,
        )
    }

    override fun keyPressed(key: KeyInput): Boolean =
        super.keyPressed(key) || currentController()?.keyPressed(key) == true

    override fun charTyped(typed: CharInput): Boolean =
        super.charTyped(typed) || currentController()?.charTyped(typed) == true

    override fun onClose() {
        BlackaddonsConfig.save()
    }

    companion object {
        private const val SCREEN_FRACTION = 0.8f
        private const val MIN_WIDTH = 320f
        private const val MIN_HEIGHT = 220f
        private const val EDGE_PAD = 20f


        /**
         * Opens the ClickGUI. When [section] is given, it opens on that section's tab with only that section expanded.
         */
        fun open(section: FeatureSection? = null) {
            UiManager.open(ClickGuiScreen(section))
        }
    }
}
