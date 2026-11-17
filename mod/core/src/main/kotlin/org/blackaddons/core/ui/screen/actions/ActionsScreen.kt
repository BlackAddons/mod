package org.blackaddons.core.ui.screen.actions

import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.TabStrip
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.ui.screen.actions.fields.LocationBar
import org.blackaddons.core.ui.screen.actions.graph.ActionGraphScreen
import org.blackaddons.core.ui.screen.actions.tabs.ActionsTab
import org.blackaddons.core.ui.screen.actions.tabs.ChatActionsTab
import org.blackaddons.core.ui.screen.actions.tabs.LocationsTab
import org.blackaddons.core.ui.screen.actions.tabs.ShortcutsTab
import org.blackaddons.core.ui.screen.actions.tabs.WaypointsTab
import org.blackaddons.core.ui.screen.profile.ProfileMenu

internal class ActionsScreen(initialPage: ActionsPage? = null) : ModalPanel(actionTr("title", "Actions")) {

    override val closeOnOutsideClick: Boolean get() = false

    override val panelWidth: Float
        get() = widthShare(0.85f, min = 620f)

    override val panelHeight: Float
        get() = heightShare(0.8f, min = 380f)

    private val chatTab = ChatActionsTab(widgets)
    private val waypointsTab = WaypointsTab(widgets)
    private val shortcutsTab = ShortcutsTab(widgets)
    private val locationsTab = LocationsTab(widgets)

    private var activeTab = initialPage?.id ?: lastActiveTab

    private val tab: ActionsTab
        get() = when (activeTab) {
            ActionsPage.WAYPOINT.id -> waypointsTab
            ActionsPage.SHORTCUT.id -> shortcutsTab
            ActionsPage.LOCATION.id -> locationsTab
            else -> chatTab
        }

    private val tabBar = widgets.add(TabStrip(
        tabs = ActionsPage.entries.map { TabStrip.Tab(id = it.id, label = it.label) },
        activeId = activeTab,
        onSelect = { id ->
            activeTab = id
            lastActiveTab = id
        },
    ))

    private val profileButton = widgets.add(Button("") { ProfileMenu.open(ActionProfiles, {}) })

    private val addGroupButton = widgets.add(Button(actionTr("add_group", "+ Add Group")) { tab.addGroup() })

    private val addActionButton = widgets.add(Button(actionTr("add_action", "+ Add Action")) { tab.onAdd() }.apply { accent = true })

    private val graphButton = widgets.add(Button(actionTr("graph.open", "Graph View")) {
        ActionGraphScreen.open(ActionsPage.entries.firstOrNull { it.id == activeTab } ?: ActionsPage.CHAT, fromList = true)
    })

    init {
        // Lists that aren't drawn get neither scrollbar nor wheel input.
        listOf(chatTab, waypointsTab, shortcutsTab, locationsTab).forEach { widgets.add(it.list) }
        lastActiveTab = activeTab
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        tabBar.render(panelX, panelY, mouseX, mouseY)

        profileButton.label = ActionStorage.activeProfile.name
        val profileW = profileButton.naturalWidth().coerceIn(PROFILE_MIN_W, PROFILE_MAX_W)
        val header = row(spacing = Theme.PADDING, align = Align.CENTER, width = contentW) {
            cell(profileW, 24f) { cx, cy -> profileButton.render(cx, cy, profileW, 24f, mouseX, mouseY) }
            flexSpacer()
            cell(100f, 24f) { cx, cy -> addGroupButton.render(cx, cy, 100f, 24f, mouseX, mouseY) }
            val graphW = (graphButton.naturalWidth() + Theme.PADDING).coerceAtLeast(GRAPH_MIN_W)
            cell(graphW, 24f) { cx, cy -> graphButton.render(cx, cy, graphW, 24f, mouseX, mouseY) }
            cell(110f, 24f) { cx, cy -> addActionButton.render(cx, cy, 110f, 24f, mouseX, mouseY) }
        }

        val showsLocationBar = activeTab == ActionsPage.LOCATION.id
        val listH = contentH - header.height -
            (if (showsLocationBar) LocationBar.HEIGHT + Theme.PADDING else 0f) - Theme.PADDING

        val layout = column(spacing = Theme.PADDING, width = contentW) {
            add(header)
            if (showsLocationBar) {
                cell(contentW, LocationBar.HEIGHT) { cx, cy -> LocationBar.draw(cx, cy) }
            }
            cell(contentW, listH) { cx, cy ->
                widgets.clickable(cx, cy, contentW, listH, Keys.MOUSE_RIGHT) { ActionMenu.list() }
                tab.render(cx, cy, contentW, listH)
            }
        }

        layout.draw(contentX, contentY)
    }

    override fun onEscape() {
        ActionStorage.save()
    }

    companion object {
        private const val PROFILE_MIN_W = 80f
        private const val PROFILE_MAX_W = 160f
        private const val GRAPH_MIN_W = 90f

        private var lastActiveTab = ActionsPage.CHAT.id

        internal var lastPage: ActionsPage
            get() = ActionsPage.entries.firstOrNull { it.id == lastActiveTab } ?: ActionsPage.CHAT
            set(value) {
                lastActiveTab = value.id
            }

        /** Pushed, not opened, so Esc goes back to the ClickGUI. */
        fun open(page: ActionsPage? = null) {
            UiManager.push(ActionsScreen(page))
        }
    }
}
