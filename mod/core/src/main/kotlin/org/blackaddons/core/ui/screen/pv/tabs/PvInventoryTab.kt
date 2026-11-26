package org.blackaddons.core.ui.screen.pv.tabs

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.PreviewGear
import org.blackaddons.api.skyblock.profile.*
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.*
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.TabStrip
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.profile.SkinTextures
import org.blackaddons.core.ui.screen.pv.parts.PvInventoryGrid
import org.blackaddons.core.ui.screen.pv.parts.PvItemSearch
import org.blackaddons.core.ui.screen.pv.parts.PvStoragePager
import org.blackaddons.core.ui.screen.pv.parts.PvTurntable

private val tr = I18n.scope("blackaddons.pv.inventory_detail")

internal class PvInventoryTab {

    val widgets = WidgetHost()
    private val scroll = widgets.add(Scroll())
    private val turntable = widgets.add(PvTurntable(key = PREVIEW_KEY))
    private val reveal = Animation(durationMs = Theme.ANIM_NORMAL.toLong())
    private val wardrobePager = PvStoragePager(widgets, "blackaddons.pv.inventory_detail.wardrobe", "Wardrobe")
    private val enderPager = PvStoragePager(widgets, "blackaddons.pv.inventory_detail.ender_chest", "Ender Chest")
    private val backpackPager = PvStoragePager(widgets, "blackaddons.pv.inventory_detail.backpacks", "Backpacks")
    private val pagers = mapOf(
        Page.WARDROBE to wardrobePager,
        Page.ENDER_CHEST to enderPager,
        Page.BACKPACKS to backpackPager,
    )
    private val search = PvItemSearch<Location>()
    private val searchField = widgets.add(TextInput(placeholder = tr("search", "Search items...")))

    private var page = Page.INVENTORY
    private var tabsFor: Set<Page>? = null
    private var tabs = widgets.add(newTabs(Page.entries.filter { it.always }))

    // A result opens its page with the hits marked, until the search text changes.
    private var browsing = false

    // Decoded on request. The screen asks as soon as a profile is shown, so this is usually ready already.
    private var inventoryOf: ProfileMember? = null
    private var inventory: ProfileInventory? = null

    fun reset() {
        inventoryOf = null
        inventory = null
        page = Page.INVENTORY
        tabsFor = null
        scroll.reset()
        pagers.values.forEach { it.reset() }
        search.reset()
        searchField.text = ""
        browsing = false
        reveal.set(0f)
    }

    fun hide() = turntable.hide()

    fun render(
        x: Float, y: Float, width: Float, height: Float,
        profiles: PlayerProfiles, textures: SkinTextures?, member: ProfileMember,
        mouseX: Float, mouseY: Float, deltaTicks: Float,
    ) {
        if (inventoryOf !== member) {
            inventoryOf = member
            inventory = null
            Platform.profiles.inventory(member) { if (inventoryOf === member) inventory = it }
        }
        syncTabs(member)
        syncSearch()

        val panelY = y + TabStrip.HEIGHT
        Cards.drawCard(x, panelY, width, (height - (panelY - y)).coerceAtLeast(0f))
        tabs.render(x - TAB_INSET + Theme.PADDING, panelY, mouseX, mouseY)

        // Its own row in the card: beside the tabs it ran into them on a 16:9 screen.
        val barX = x + Theme.PADDING
        val barY = panelY + Theme.PADDING
        val barW = width - Theme.PADDING * 2f
        val searchW = minOf(SEARCH_W, barW)
        searchField.render(barX + barW - searchW, barY, searchW, SEARCH_H, mouseX, mouseY)

        val bodyX = barX
        val bodyY = barY + SEARCH_H + Theme.PADDING
        val bodyW = (barW - Cards.SCROLL_GUTTER).coerceAtLeast(0f)
        val bodyH = (height - (bodyY - y) - Theme.PADDING).coerceAtLeast(0f)
        val inventory = inventory ?: return loading(bodyX, bodyY, bodyW)
        if (search.active) {
            search.index(inventory) { places(member, inventory) }
            drawSearchSummary(barX, barY, barW - searchW - Theme.PADDING)
        }
        reveal.animateTo(1f)

        val contentW = (bodyW - Cards.INSET * 2f).coerceAtLeast(0f)
        val showInventory = page == Page.INVENTORY && page.available(member) && !(search.active && !browsing)
        if (!showInventory) turntable.hide()
        val node = when {
            search.active && !browsing -> resultsNode(contentW)
            else -> pageNode(contentW, bodyH, member, inventory)
        }
        val offset = scroll.update(bodyX, bodyY, bodyW + Cards.SCROLL_GUTTER, bodyH, node.height + Cards.INSET * 2f)

        Render.pushScissor(bodyX, bodyY, bodyW + Cards.SCROLL_GUTTER, bodyH)
        Render.globalAlpha(reveal.get())
        try {
            node.draw(bodyX + Cards.INSET, bodyY + Cards.INSET - offset)
        } finally {
            Render.globalAlpha(1f)
            Render.popScissor()
        }
        scroll.draw()
        if (showInventory) drawModel(contentW, bodyH, profiles, textures, inventory, bodyX, bodyY - offset, deltaTicks)
    }

    private fun drawSearchSummary(x: Float, y: Float, width: Float) {
        if (!search.ready || width <= 0f) return
        val hits = search.hits()
        val text = tr("summary", "%d items in %d places").format(hits.sumOf { it.items.size }, hits.size)
        Render.text(text, x, Render.centerY(y, SEARCH_H, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED, maxWidth = width)
    }

    private fun loading(x: Float, y: Float, width: Float) =
        column(width = width) { note(width, tr("loading", "Loading items...")) }.draw(x, y)

    private fun syncTabs(member: ProfileMember) {
        val shown = Page.entries.filterTo(LinkedHashSet()) { it.always || it.available(member) }
        if (shown == tabsFor) return
        tabsFor = shown
        if (page !in shown) page = Page.INVENTORY
        widgets.remove(tabs)
        tabs = widgets.add(newTabs(shown.toList()))
    }

    private fun syncSearch() {
        if (searchField.text == search.query) return
        search.setQuery(searchField.text)
        browsing = false
        scroll.reset()
    }

    private fun pageNode(width: Float, height: Float, member: ProfileMember, inventory: ProfileInventory): LayoutNode {
        if (!page.available(member)) {
            return column(spacing = Theme.PADDING_SMALL, width = width) {
                sectionHeader(width, tr("api_off", "Inventory API unavailable"))
                note(width, tr("api_off_detail", "This profile does not expose this container."))
            }
        }
        val match = if (search.active) search::matches else null
        pagers[page]?.let { pager -> return pager.node(width, page.pages(inventory), match) }
        if (page == Page.INVENTORY) return inventoryNode(width, height, inventory, match)
        if (page == Page.BAGS) return bagsNode(width, member, inventory, match)
        return flatNode(width, page.title(), page.slots(inventory), match)
    }

    /** Room on the left for the model, then equipment and armor beside the main inventory, as one centered block. */
    private fun inventoryNode(
        width: Float, height: Float, inventory: ProfileInventory, match: ((ProfileItem) -> Boolean)?,
    ): LayoutNode {
        val layout = InventoryLayout.of(width, height)
        val gap = Theme.PADDING_LARGE
        val gearW = PvInventoryGrid.width(1, layout.cell) * 2f + gap
        val mainW = PvInventoryGrid.width(INVENTORY_COLUMNS, layout.cell)
        return column(align = Align.CENTER, width = width) {
            row(spacing = gap) {
                cell(layout.modelW, layout.blockH) { _, _ -> }
                column(spacing = Theme.PADDING_SMALL, width = gearW) {
                    sectionHeader(gearW, tr("gear", "Equipment / Armor"))
                    row(spacing = gap) {
                        add(PvInventoryGrid.node(1, layout.cell, inventory.equipment, GEAR_SLOTS, match))
                        add(PvInventoryGrid.node(1, layout.cell, inventory.armor.reversed(), GEAR_SLOTS, match))
                    }
                }
                column(spacing = Theme.PADDING_SMALL, width = mainW) {
                    sectionHeader(mainW, tr("inventory", "Inventory"))
                    add(PvInventoryGrid.node(INVENTORY_COLUMNS, layout.cell, inventory.main.hotbarLast(), INVENTORY_SLOTS, match))
                }
            }
        }
    }

    // After the scissor is gone: the model composites over the panel and draws through no clip of ours.
    private fun drawModel(
        width: Float, height: Float, profiles: PlayerProfiles, textures: SkinTextures?, inventory: ProfileInventory,
        bodyX: Float, bodyY: Float, deltaTicks: Float,
    ) {
        val layout = InventoryLayout.of(width, height)
        val gap = Theme.PADDING_LARGE
        val blockW = layout.modelW + gap + (PvInventoryGrid.width(1, layout.cell) * 2f + gap) + gap +
            PvInventoryGrid.width(INVENTORY_COLUMNS, layout.cell)
        val left = bodyX + Cards.INSET + (width - blockW) / 2f
        // Hypixel lists armor from the boots up.
        val armor = inventory.armor.map { it?.raw }
        val gear = PreviewGear(
            helmet = armor.getOrNull(HELMET), chestplate = armor.getOrNull(CHESTPLATE),
            leggings = armor.getOrNull(LEGGINGS), boots = armor.getOrNull(BOOTS),
        )
        turntable.draw(left, bodyY + Cards.INSET, layout.modelW, layout.blockH, profiles, textures, deltaTicks, gear)
    }

    /**
     * The cell size that fills the body: as wide as the model plus eleven columns allow, as tall as four rows allow,
     * capped so items don't turn into blurry posters.
     */
    private class InventoryLayout(val cell: Float, val modelW: Float, val blockH: Float) {
        companion object {
            fun of(width: Float, height: Float): InventoryLayout {
                val gap = Theme.PADDING_LARGE
                val headerH = Cards.HEADER_H + Theme.PADDING_SMALL
                val byWidth = (width - MODEL_RATIO * headerH - gap * 3f) / (MODEL_RATIO * GEAR_SLOTS + GRID_COLUMNS)
                val byHeight = (height - headerH - Cards.INSET * 2f) / GEAR_SLOTS
                val cell = minOf(byWidth, byHeight, INVENTORY_MAX_CELL).coerceAtLeast(PvInventoryGrid.MIN_CELL)
                val blockH = headerH + PvInventoryGrid.height(GEAR_SLOTS, 1, cell)
                return InventoryLayout(cell, blockH * MODEL_RATIO, blockH)
            }
        }
    }

    private fun flatNode(width: Float, title: String, slots: List<ProfileItem?>, match: ((ProfileItem) -> Boolean)?): LayoutNode =
        column(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = width) {
            sectionHeader(width, title, if (slots.isEmpty()) null else tr("count", "%d items").format(slots.count { it != null }))
            if (slots.isEmpty()) {
                note(width, tr("empty", "No items recorded."))
            } else {
                add(PvInventoryGrid.node(STORAGE_COLUMNS, PvInventoryGrid.cell(width, STORAGE_COLUMNS), slots, match = match))
            }
        }

    /** The potion bag, quiver and fishing bag one under another: as three tabs they didn't fit beside the rest. */
    private fun bagsNode(width: Float, member: ProfileMember, inventory: ProfileInventory, match: ((ProfileItem) -> Boolean)?): LayoutNode =
        column(spacing = Theme.PADDING_LARGE, width = width) {
            Bag.entries.filter { it.available(member) }.forEach { bag -> add(flatNode(width, bag.title(), bag.slots(inventory), match)) }
        }

    /** Every container that has the search text, each under a header that opens it with the hits marked. */
    private fun resultsNode(width: Float): LayoutNode {
        if (!search.ready) return column(width = width) { note(width, tr("searching", "Searching...")) }
        val hits = search.hits()
        if (hits.isEmpty()) return column(width = width) { note(width, tr("no_results", "Nothing matches \"%s\".").format(search.query.trim())) }
        val cell = PvInventoryGrid.cell(width, STORAGE_COLUMNS)
        return column(spacing = Theme.PADDING, width = width) {
            hits.forEach { hit ->
                column(spacing = Theme.PADDING_SMALL, width = width) {
                    cell(width, Cards.HEADER_H) { hx, hy ->
                        Cards.drawHeader(hx, hy, width, hit.location.label(), tr("open", "Open (%d)").format(hit.items.size), Theme.ACCENT)
                        widgets.clickable(hx, hy, width, Cards.HEADER_H) { open(hit.location) }
                    }
                    add(PvInventoryGrid.node(STORAGE_COLUMNS, cell, hit.items))
                }
            }
        }
    }

    private fun open(location: Location) {
        page = location.page
        location.storage?.let { pagers[location.page]?.select(it) }
        browsing = true
        scroll.reset()
        widgets.remove(tabs)
        tabs = widgets.add(newTabs(tabsFor.orEmpty().toList()))
    }

    private fun places(member: ProfileMember, inventory: ProfileInventory): List<PvItemSearch.Place<Location>> = buildList {
        fun add(page: Page, storage: Int?, slots: List<ProfileItem?>) {
            if (page.available(member)) add(PvItemSearch.Place(Location(page, storage), slots))
        }
        add(Page.INVENTORY, null, inventory.armor.reversed() + inventory.equipment + inventory.main.hotbarLast())
        Page.entries.filter { it.flat }.forEach { add(it, null, it.slots(inventory)) }
        Bag.entries.forEach { bag ->
            if (bag.available(member)) add(PvItemSearch.Place(Location(Page.BAGS, bag.ordinal), bag.slots(inventory)))
        }
        pagers.keys.forEach { page -> page.pages(inventory).forEachIndexed { index, storage -> add(page, index, storage.slots) } }
    }.sortedBy { it.location.page.ordinal }

    /** Where a hit is: a page of the tab, and for paged containers the index in that page's list. */
    private inner class Location(val page: Page, val storage: Int?) {
        fun label(): String {
            if (page == Page.BAGS) return storage?.let { Bag.entries.getOrNull(it)?.title() } ?: page.title()
            val inventory = inventory ?: return page.title()
            val number = storage?.let { page.pages(inventory).getOrNull(it)?.index } ?: return page.title()
            return page.title() + " · " + I18n.trOr("blackaddons.pv.inventory_detail.page", "Page %d").format(number)
        }

        override fun equals(other: Any?) = other is Location && other.page == page && other.storage == storage
        override fun hashCode() = page.hashCode() * 31 + (storage ?: -1)
    }

    /** Hypixel sends the hotbar first. Minecraft shows it last. */
    private fun List<ProfileItem?>.hotbarLast(): List<ProfileItem?> =
        if (size < HOTBAR_SLOTS) this else drop(HOTBAR_SLOTS) + take(HOTBAR_SLOTS)

    private fun newTabs(shown: List<Page>) = TabStrip(
        shown.map { shownPage ->
            TabStrip.Tab(shownPage.id, shownPage.title(), badge = { if (search.active) hitsOn(shownPage) else 0 })
        },
        activeId = page.id,
        onSelect = { selected -> Page.fromId(selected)?.let { page = it; scroll.reset(); if (search.active) browsing = true } },
        panelColor = Theme.SURFACE,
        tabColor = Theme.BACKGROUND,
    )

    private fun hitsOn(page: Page): Int = search.hits().filter { it.location.page == page }.sumOf { it.items.size }

    /**
     * [always] pages show even when the API hides them, to say so. The bags only show when the profile has them. [flat]
     * pages are a single grid.
     */
    private enum class Page(
        val id: String,
        val inventoryPage: InventoryPage,
        val fallback: String,
        val always: Boolean = false,
        val flat: Boolean = false,
    ) {
        INVENTORY("inventory", InventoryPage.INVENTORY, "Inventory", always = true),
        ACCESSORIES("accessories", InventoryPage.ACCESSORIES, "Accessories", flat = true),
        WARDROBE("wardrobe", InventoryPage.WARDROBE, "Wardrobe", always = true),
        ENDER_CHEST("ender_chest", InventoryPage.ENDER_CHEST, "Ender Chest", always = true),
        BACKPACKS("backpacks", InventoryPage.BACKPACKS, "Backpacks", always = true),
        VAULT("vault", InventoryPage.VAULT, "Vault", always = true, flat = true),
        BAGS("bags", InventoryPage.POTIONS, "Bags");

        fun title(): String = tr(id, fallback)
        fun available(member: ProfileMember): Boolean =
            if (this == BAGS) Bag.entries.any { it.available(member) } else inventoryPage in member.inventoryPages

        fun slots(inventory: ProfileInventory): List<ProfileItem?> = when (this) {
            ACCESSORIES -> inventory.accessoryBag
            VAULT -> inventory.personalVault
            else -> emptyList()
        }

        fun pages(inventory: ProfileInventory): List<InventoryStorage> = when (this) {
            WARDROBE -> inventory.wardrobe
            ENDER_CHEST -> inventory.enderChest
            BACKPACKS -> inventory.backpacks
            else -> emptyList()
        }

        companion object {
            fun fromId(id: String): Page? = entries.firstOrNull { it.id == id }
        }
    }

    private enum class Bag(val id: String, val inventoryPage: InventoryPage, val fallback: String, val slots: (ProfileInventory) -> List<ProfileItem?>) {
        POTIONS("potion_bag", InventoryPage.POTIONS, "Potion Bag", ProfileInventory::potionBag),
        QUIVER("quiver", InventoryPage.QUIVER, "Quiver", ProfileInventory::quiver),
        FISHING("fishing_bag", InventoryPage.FISHING, "Fishing Bag", ProfileInventory::fishingBag);

        fun title(): String = tr(id, fallback)
        fun available(member: ProfileMember): Boolean = inventoryPage in member.inventoryPages
    }

    private companion object {
        const val PREVIEW_KEY = "pv-inventory"
        const val TAB_INSET = 12f
        const val SEARCH_W = 220f
        const val SEARCH_H = 20f
        const val GEAR_SLOTS = 4
        const val INVENTORY_COLUMNS = 9
        const val INVENTORY_SLOTS = 36
        const val HOTBAR_SLOTS = 9
        const val STORAGE_COLUMNS = 9

        // Gear and main inventory side by side: two gear columns and nine inventory ones.
        const val GRID_COLUMNS = 11

        // The model frame's width as a multiple of its height.
        const val MODEL_RATIO = 0.55f
        const val INVENTORY_MAX_CELL = 52f

        // Hypixel's armor order, boots first.
        const val BOOTS = 0
        const val LEGGINGS = 1
        const val CHESTPLATE = 2
        const val HELMET = 3
    }
}
