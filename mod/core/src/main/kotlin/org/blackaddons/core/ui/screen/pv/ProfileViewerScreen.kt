package org.blackaddons.core.ui.screen.pv

import org.blackaddons.api.Platform
import org.blackaddons.api.host.AccountLookup
import org.blackaddons.api.skyblock.profile.CoopMember
import org.blackaddons.api.skyblock.profile.PlayerProfiles
import org.blackaddons.api.skyblock.profile.SkyblockProfile
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.*
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.LatestOnly
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.BackendNotifier
import org.blackaddons.core.profile.*
import org.blackaddons.core.ui.screen.pv.parts.ProfileBadges
import org.blackaddons.core.ui.screen.pv.parts.RecentPlayersColumn
import org.blackaddons.core.ui.screen.pv.tabs.*
import java.util.UUID
import kotlin.time.TimeSource

private val tr = I18n.scope("blackaddons.pv")

/**
 * Profile Viewer shell: the centered panel, its chrome (tabs above, recent players right, picker and search below) and
 * the lookup. Each tab renders itself.
 */
internal class ProfileViewerScreen(private var query: String) : Panel() {

    @Volatile
    private var result: LoadResult? = null

    private val profileDropdown = widgets.add(Dropdown().apply { dropUp = true })

    // The player, then the other members of a coop profile. Picking one looks them up.
    private val memberDropdown = widgets.add(Dropdown().apply {
        dropUp = true
        onSelect = ::memberSelected
    })
    private var membersFor: SkyblockProfile? = null

    // Names the backend didn't have, looked up here (the game's own lookup first). Kept while the screen is open.
    private val coopNames = HashMap<UUID, String>()
    private val searchField = widgets.add(TextInput(placeholder = tr("search", "Look up player...")).apply {
        onEnter = {
            val name = text.trim()
            if (name.isNotEmpty()) {
                clear()
                load(name)
            }
        }
    })

    private var builtFrom: PlayerProfiles? = null

    private val tabs = PvTabRegistry(listOf(
        PvOverviewTab().let { tab ->
            ViewerSection(
                "overview", "Overview", LucideIcons.overview, tab::reset, tab::hide,
                tab.widgets,
                keepsPlayerPreview = true,
            ) { context ->
                val c = context.content
                tab.render(c.x, c.y, c.width, c.height, context.profiles, context.textures, context.profile.member,
                    context.mouseX, context.mouseY, context.deltaTicks)
            }
        },
        PvSkillsTab().let { tab ->
            ViewerSection(
                "skills", "Skills", LucideIcons.skills, tab::reset, tab::hide,
                tab.widgets,
            ) { context ->
                val c = context.content
                tab.render(c.x, c.y, c.width, c.height, context.profile.member)
            }
        },
        PvRecentTab(::load).let { tab ->
            ViewerSection(
                "recent", "Recent", LucideIcons.usersRound, tab::reset, tab::hide,
                tab.widgets,
                overlay = tab::renderOverlay,
            ) { context ->
                val c = context.content
                tab.render(c.x, c.y, c.width, c.height, context.profiles.uuid.dashless, context.profile)
            }
        },
        PvDungeonsTab().let { tab ->
            ViewerSection(
                "dungeons", "Dungeons", LucideIcons.dungeon, tab::reset, tab::hide,
                tab.widgets,
            ) { context ->
                val c = context.content
                tab.render(c.x, c.y, c.width, c.height, context.profile.member)
            }
        },
        PvCa50Tab().let { tab ->
            ViewerSection(
                "ca50", "CA50 Calculator", LucideIcons.calculator, tab::reset, tab::hide,
                tab.widgets,
                overlay = tab::renderOverlay,
            ) { context ->
                val c = context.content
                tab.render(c.x, c.y, c.width, c.height, context.profile.member, context.mouseX, context.mouseY)
            }
        },
        PvCombatTab().let { tab ->
            ViewerSection(
                "combat", "Combat", LucideIcons.combat, tab::reset, tab::hide,
                tab.widgets,
            ) { context ->
                val c = context.content
                tab.render(c.x, c.y, c.width, c.height, context.profile.member)
            }
        },
        PvInventoryTab().let { tab ->
            ViewerSection(
                "inventory", "Inventory", LucideIcons.inventory, tab::reset, tab::hide,
                tab.widgets,
            ) { context ->
                val c = context.content
                tab.render(
                    c.x, c.y, c.width, c.height, context.profiles, context.textures, context.profile.member,
                    context.mouseX, context.mouseY, context.deltaTicks,
                )
            }
        },
        PvPetsTab().let { tab ->
            ViewerSection(
                "pets", "Pets", LucideIcons.pets, tab::reset, tab::hide,
                tab.widgets,
            ) { context ->
                val c = context.content
                tab.render(c.x, c.y, c.width, c.height, context.profile.member)
            }
        },
        PvNetworthTab().let { tab ->
            ViewerSection(
                "networth", "Networth", LucideIcons.networth, tab::reset, tab::hide,
                tab.widgets,
            ) { context ->
                val c = context.content
                tab.render(c.x, c.y, c.width, c.height, context.profile.member.networth)
            }
        },
    ), initialId = "overview")

    private val tabBar = widgets.add(TabStrip(
        tabs = tabs.all.map(::sectionTab),
        activeId = tabs.active.id,
        onSelect = tabs::select,
    ))

    private val recentColumn = widgets.add(RecentPlayersColumn(onOpen = { load(it.name) }))

    // Only the visible section gets input, and not while search or the picker has focus.
    private val activeSection = widgets.add(object : InputTarget {
        private val input get() = tabs.active.input
        override fun mouseClicked(mouse: MouseButtonInput) = input.mouseClicked(mouse)
        override fun mouseReleased(mouse: MouseButtonInput) = input.mouseReleased(mouse)
        override fun mouseDragged(drag: MouseDragInput) =
            input.mouseDragged(drag)

        override fun mouseScrolled(wheel: MouseScrollInput) = input.mouseScrolled(wheel)
        override fun keyPressed(key: KeyInput) = input.keyPressed(key)
        override fun charTyped(typed: CharInput) = input.charTyped(typed)
        override val isOverlaying: Boolean get() = input.isOverlaying
        override var focused: Boolean
            get() = input.focused
            set(value) {
                input.focused = value
            }
    })

    fun onResult(result: LoadResult) {
        resultAt = TimeSource.Monotonic.markNow()
        firstFramePending = result is LoadResult.Loaded
        // Set after the plain fields, because the render thread reads them once it sees this @Volatile one.
        this.result = result
        if (result is LoadResult.Loaded) {
            val p = result.profiles
            RecentPlayers.record(p.uuid.dashless, p.name, result.textures?.value)
        }
    }

    override fun onShow() {
        BackendNotifier.ensureConnected()
        load(query)
    }

    private val lookups = LatestOnly()

    // For the `pv-load` dev log. The lookup's own phases are traced on the worker, these cover the screen.
    private var loadStartedAt = TimeSource.Monotonic.markNow()
    private var resultAt = TimeSource.Monotonic.markNow()
    private var firstFramePending = false

    private fun load(name: String) = lookUp(name) { ProfileService.load(name) }

    private fun memberSelected(index: Int) {
        val profile = membersFor ?: return
        val member = profile.coop.getOrNull(index - 1) ?: return
        lookUp(nameOf(member)) { ProfileService.load(member.uuid) }
    }

    private fun lookUp(label: String, work: () -> LoadResult) {
        query = label
        result = null
        loadStartedAt = TimeSource.Monotonic.markNow()
        firstFramePending = false
        builtFrom = null
        membersFor = null
        // Right away, because a list fading out over the loading screen would never finish: its bar isn't drawn there.
        profileDropdown.closeImmediately()
        memberDropdown.closeImmediately()
        tabs.resetAll()
        // Off-thread lookup into a @Volatile field. A throw becomes Unavailable, so the screen never sticks on loading.
        OffThread.run(
            name = "pv",
            work = work,
            onFailure = { LoadResult.Unavailable },
            onResult = lookups.next(::onResult),
        )
    }

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val w = UiManager.screenWidth
        val h = UiManager.screenHeight
        Render.rect(0f, 0f, w, h, Theme.OVERLAY_DIM)

        // Size capped, so the chrome still fits in the margins.
        val panelW = minOf(maxOf(w * 0.78f, 520f), w - 2f * RIGHT_CHROME)
        val panelH = minOf(maxOf(h * 0.80f, 320f), h - 2f * TOP_CHROME)
        val panelX = (w - panelW) / 2f
        val panelY = (h - panelH) / 2f

        // Tabs after the panel, because the active underline sits across the panel edge.
        Render.rect(panelX, panelY, panelW, panelH, Theme.BACKGROUND, Theme.RADIUS_LARGE)
        tabBar.render(panelX, panelY, mouseX, mouseY)
        recentColumn.render(panelX, panelY, panelW, panelH, mouseX, mouseY)

        val bottomBarY = panelY + panelH - Theme.PADDING_LARGE - BAR_H
        val content = ContentBounds(
            x = panelX + Theme.PADDING_LARGE,
            y = panelY + Theme.PADDING_LARGE,
            width = panelW - Theme.PADDING_LARGE * 2f,
            height = bottomBarY - panelY - Theme.PADDING_LARGE - Theme.PADDING,
        )

        val r = result
        val loaded = r as? LoadResult.Loaded
        val profiles = loaded?.profiles
        val profile = profiles?.let {
            syncDropdown(it)
            it.profiles.getOrNull(profileDropdown.selectedIndex)
        }
        if (profiles != null && profile != null) {
            syncMembers(profiles, profile)
            renderTab(content, profiles, loaded.textures, profile, mouseX, mouseY, deltaTicks)
        } else {
            Platform.playerPreview?.clear()
            tabs.hideAll()
            renderStatus(panelX, panelY, panelW, panelH, r)
        }

        renderBottomBar(content.x, bottomBarY, content.width, profiles != null, profile?.coop?.isNotEmpty() == true, mouseX, mouseY)
    }

    private fun renderTab(
        content: ContentBounds, profiles: PlayerProfiles, textures: SkinTextures?, profile: SkyblockProfile,
        mouseX: Float, mouseY: Float, deltaTicks: Float,
    ) {
        tabs.hideInactive()
        if (!tabs.active.keepsPlayerPreview) Platform.playerPreview?.clear()
        val frameStart = TimeSource.Monotonic.markNow()
        tabs.active.render(TabContext(content, profiles, textures, profile, mouseX, mouseY, deltaTicks))
        if (firstFramePending) {
            firstFramePending = false
            val now = TimeSource.Monotonic.markNow()
            LoadTrace.write(
                "$query: on screen ${(now - loadStartedAt).inWholeMilliseconds} ms after the lookup started " +
                    "(result to frame ${(frameStart - resultAt).inWholeMilliseconds} ms, " +
                    "first ${tabs.active.id} frame ${(now - frameStart).inWholeMilliseconds} ms)",
            )
        }
    }

    // Drawn after the player model. Inside render() it showed behind the avatar's legs.
    override fun renderOverlay(mouseX: Float, mouseY: Float) {
        if (result !is LoadResult.Loaded) return
        profileDropdown.renderOverlay(mouseX, mouseY)
        memberDropdown.renderOverlay(mouseX, mouseY)
        tabs.active.overlay(mouseX, mouseY)
    }

    private fun renderStatus(panelX: Float, panelY: Float, panelW: Float, panelH: Float, r: LoadResult?) {
        val (text, color) = when (r) {
            null -> tr("loading", "Loading %s...").format(query) to Theme.TEXT_SECONDARY
            LoadResult.PlayerNotFound ->
                tr("not_found", "Player \"%s\" not found.").format(query) to Theme.DANGER
            is LoadResult.NoProfiles ->
                tr("no_profiles", "%s has no Skyblock profiles.").format(r.username) to Theme.TEXT_SECONDARY
            LoadResult.Unavailable ->
                tr("unavailable", "Profile data is unavailable right now. Try again later.") to Theme.DANGER
            is LoadResult.Loaded -> return
        }
        Render.textCentered(
            text, panelX + panelW / 2f, panelY + panelH / 2f - Theme.FONT_TITLE,
            Theme.FONT_TITLE, color, Fonts.medium,
        )
    }

    private fun renderBottomBar(
        x: Float, y: Float, width: Float, loaded: Boolean, coop: Boolean, mouseX: Float, mouseY: Float,
    ) {
        val searchW = (width * 0.4f).coerceIn(140f, 240f)
        searchField.render(x + width - searchW, y, searchW, BAR_H, mouseX, mouseY)
        if (!loaded) return
        profileDropdown.render(x, y, DROPDOWN_W, BAR_H, mouseX, mouseY)
        if (coop) memberDropdown.render(x + DROPDOWN_W + Theme.PADDING, y, MEMBER_DROPDOWN_W, BAR_H, mouseX, mouseY)
    }

    private fun syncDropdown(profiles: PlayerProfiles) {
        if (builtFrom === profiles) return
        builtFrom = profiles
        profileDropdown.items = profiles.profiles.map {
            Dropdown.Item(it.cuteName + if (it.selected) "  ●" else "", ProfileBadges.gamemodeIcon(it.mode)) // ● is the easiest way here, too lazy to draw a circle
        }
        profileDropdown.selectedIndex = profiles.profiles.indexOfFirst { it.selected }.coerceAtLeast(0)
    }

    private fun syncMembers(profiles: PlayerProfiles, profile: SkyblockProfile) {
        if (membersFor === profile) return
        membersFor = profile
        // Start the slow item decode now, so the Inventory tab rarely has to wait for it.
        Platform.profiles.inventory(profile.member) {}
        memberDropdown.closeImmediately()
        fillMembers(profiles, profile)
        memberDropdown.selectedIndex = 0
        profile.coop.filter { it.name == null && it.uuid !in coopNames }.forEach { member ->
            Platform.mojang.byUuid(member.uuid) { lookup ->
                if (lookup !is AccountLookup.Found) return@byUuid
                coopNames[member.uuid] = lookup.name
                if (membersFor === profile) fillMembers(profiles, profile)
            }
        }
    }

    private fun fillMembers(profiles: PlayerProfiles, profile: SkyblockProfile) {
        memberDropdown.items = listOf(Dropdown.Item(profiles.name)) + profile.coop.map { Dropdown.Item(nameOf(it)) }
    }

    private fun nameOf(member: CoopMember): String =
        member.name ?: coopNames[member.uuid] ?: member.uuid.toString().take(UNKNOWN_NAME_CHARS)

    // A long tooltip gets the scroll before anything under it.
    override fun mouseScrolled(wheel: MouseScrollInput): Boolean =
        Tooltip.mouseScrolled(wheel.amount) || super.mouseScrolled(wheel)

    override fun onClose() {
        Platform.playerPreview?.clear()
    }

    companion object {
        private const val BAR_H = 22f
        private const val DROPDOWN_W = 180f
        private const val MEMBER_DROPDOWN_W = 140f

        /**
         * A coop member without a name yet (both the backend and the lookup are still pending) shows the start of their
         * UUID.
         */
        private const val UNKNOWN_NAME_CHARS = 8

        private fun sectionTab(section: ViewerSection) = TabStrip.Tab(
            id = section.id,
            label = tr("tab.${section.id}", section.fallbackLabel),
            icon = section.icon,
            disabledHint = tr("tab.soon", "Coming soon"),
        )

        /** Space kept free of the panel for the tab strip (top) and the recent-players column (right). */
        private const val TOP_CHROME = 32f
        private const val RIGHT_CHROME = 54f

        fun open(query: String) {
            UiManager.open(ProfileViewerScreen(query))
        }
    }

    private data class ContentBounds(val x: Float, val y: Float, val width: Float, val height: Float)

    private data class TabContext(
        val content: ContentBounds,
        val profiles: PlayerProfiles,
        val textures: SkinTextures?,
        val profile: SkyblockProfile,
        val mouseX: Float,
        val mouseY: Float,
        val deltaTicks: Float,
    )

    private data class ViewerSection(
        override val id: String,
        val fallbackLabel: String,
        val icon: VectorIcon,
        private val onReset: () -> Unit,
        private val onHide: () -> Unit,
        val input: InputTarget,
        val keepsPlayerPreview: Boolean = false,
        val overlay: (Float, Float) -> Unit = { _, _ -> },
        val render: (TabContext) -> Unit,
    ) : RegisteredPvTab {
        override fun reset() = onReset()

        // A focused field in a hidden section would keep eating keys.
        override fun hide() {
            input.focused = false
            onHide()
        }
    }


}

internal interface RegisteredPvTab {
    val id: String
    fun reset()
    fun hide()
}

internal class PvTabRegistry<T : RegisteredPvTab>(
    tabs: List<T>,
    initialId: String = tabs.firstOrNull()?.id.orEmpty(),
) {
    val all = tabs.toList()
    private val byId = all.associateBy(RegisteredPvTab::id)
    private val initial = byId[initialId]

    init {
        require(all.isNotEmpty()) { "Profile Viewer needs at least one tab" }
        require(byId.size == all.size) { "Profile Viewer tab ids must be unique" }
        require(initial != null) { "Unknown initial Profile Viewer tab: $initialId" }
    }

    var active: T = requireNotNull(initial)
        private set

    fun select(id: String) {
        byId[id]?.let { active = it }
    }

    fun resetAll() = all.forEach { it.reset() }

    fun hideAll() = all.forEach { it.hide() }

    fun hideInactive() = all.forEach { if (it !== active) it.hide() }
}
