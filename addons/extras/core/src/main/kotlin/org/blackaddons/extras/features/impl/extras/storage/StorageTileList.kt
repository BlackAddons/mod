package org.blackaddons.extras.features.impl.extras.storage

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.gui.ScreenDraw
import org.blackaddons.api.ui.anim.AnimationMap
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.text.TextInput
import kotlin.math.roundToInt

/**
 * Scrollable grid of page tiles, owns the [Scroll] and the rename field. The open page's tile uses the live container,
 * with real slots parked under its cells so moves stay native.
 */
internal class StorageTileList(private val onOpen: (StorageRef) -> Unit) {

    private val scroll = Scroll()
    private val slotHover = AnimationMap<Pair<String, Int>>()

    private var scrollY = 0
    private var top = 0
    private var bottom = 0
    private var areaX = 0
    private var areaW = 0
    private var tiles: List<StorageLayout.Tile> = emptyList()

    // Custom names for this profile, plus the rename in progress.
    private var names: Map<String, String> = emptyMap()
    private var renaming: String? = null
    private val renameInput = TextInput(maxLength = StorageTheme.NAME_LIMIT).apply {
        onEnter = { commitRename() }
        onEscape = { cancelRename() }
    }

    // The open page and its live items, for this frame.
    private var activeKey: String? = null
    private var liveBody: List<CapturedItem?>? = null

    private var revealedKey: String? = null

    /** Layout cache: building walks every page and item, and inputs barely change between frames. */
    private var layoutKey: LayoutKey? = null
    private var built: StorageLayout.Layout? = null

    private data class LayoutKey(
        val version: Int,
        val search: String,
        val areaX: Int,
        val areaWidth: Int,
        val top: Int,
        val activeKey: String?,
        val perRow: Int,
        // Identity check: the open page gets rebuilt each tick, so a new instance means items might have moved.
        val live: Int,
    )

    /** Returns the hovered cached item's raw form. */
    fun renderUi(
        metrics: ContainerMetrics, search: String, activeKey: String?, live: CapturedContainer?,
        mouseX: Int, mouseY: Int,
    ): String? {
        names = StorageCache.names()
        this.activeKey = activeKey
        liveBody = live?.items

        top = StorageTheme.TOP_MARGIN
        bottom = metrics.inventoryTop - StorageTheme.SEARCH_H - 12
        areaX = StorageTheme.MARGIN
        areaW = metrics.width - StorageTheme.MARGIN * 2

        val entries = StorageCache.entries()
        if (entries.isEmpty()) {
            tiles = emptyList()
            if (live != null) parkLivePage()
            message(metrics, storageTr("open_to_load", "Open the /storage menu to load it."))
            return null
        }

        val key = LayoutKey(
            StorageCache.version, search, areaX, areaW, top, activeKey, StoragePanel.pagesPerRow(), System.identityHashCode(live),
        )
        val layout = built?.takeIf { key == layoutKey } ?: run {
            // The open page's tile is live, the others come from the cache.
            val lookup = { k: String -> if (k == activeKey) live else StorageCache.get(k) }
            val pad = StorageTheme.CARD_PAD
            val innerW = areaW - pad * 2 - Scroll.LANE_WIDTH.toInt()
            StorageLayout.build(entries, lookup, search, areaX + pad, innerW, top + StorageTheme.LIST_TOP_INSET, key.perRow).also {
                built = it
                layoutKey = key
            }
        }
        tiles = layout.tiles
        if (activeKey != revealedKey) reveal(activeKey)

        val viewH = (bottom - top).toFloat()
        val contentH = layout.contentHeight + StorageTheme.LIST_TOP_INSET
        scrollY = scroll.update(areaX.toFloat(), top.toFloat(), areaW.toFloat(), viewH, contentH.toFloat()).roundToInt()
        Render.pushScissor(areaX, top, areaW, bottom - top)
        val hovered = renderTiles(layout, mouseX, mouseY)
        Render.popScissor()
        scroll.draw()

        if (search.isNotBlank() && layout.tiles.isEmpty()) {
            message(metrics, storageTr("no_match", "No items match your search."))
        }
        return hovered
    }

    private fun reveal(key: String?) {
        val tile = tiles.firstOrNull { it.ref.key == key }
        if (key != null && tile == null) return
        revealedKey = key
        tile ?: return
        val pad = StorageTheme.CARD_PAD
        val current = scroll.targetOffset
        val cardTop = tile.y - pad - top
        val cardBottom = tile.y + tile.h + pad - bottom
        if (cardTop < current) scroll.scrollTo(cardTop.toFloat())
        else if (cardBottom > current) scroll.scrollTo(minOf(cardBottom, cardTop).toFloat())
    }

    private fun message(metrics: ContainerMetrics, text: String) =
        Render.textCentered(text, metrics.width / 2, top + (bottom - top) / 2, TextStyle.BODY_MUTED)

    private fun renderTiles(layout: StorageLayout.Layout, mouseX: Int, mouseY: Int): String? {
        val slot = StorageTheme.SLOT
        val pad = StorageTheme.CARD_PAD
        val pointerInList = mouseY in top until bottom
        var hovered: String? = null
        // If search hid the open page, its slots have to leave their vanilla positions.
        if (liveBody != null && layout.tiles.none { it.ref.key == activeKey }) parkLivePage()
        for (tile in layout.tiles) {
            val ty = tile.y - scrollY
            val gy = tile.gridY - scrollY
            val live = tile.ref.key == activeKey && liveBody != null
            // Park real slots under this grid for native clicks, even while scrolled out.
            if (live) layoutLivePage(1, tile.cols, tile.x, gy, slot)
            if (ty + tile.h < top || ty > bottom) continue

            val cardW = tile.w - 2 + pad * 2
            val cardH = tile.h - 2 + pad * 2
            val overCard = pointerInList && hit(mouseX, mouseY, tile.x - pad, ty - pad, cardW, cardH)
            Render.glass(tile.x - pad, ty - pad, cardW, cardH, Glass.CARD, if (overCard && !live) 1f else 0f)
            if (live) Render.hollowRect(tile.x - pad, ty - pad, cardW, cardH, 1f, Theme.ACCENT, Glass.CARD.radius)

            renderHeader(tile, ty, mouseX, mouseY)
            val key = tile.ref.key
            for (r in 0 until tile.rows) for (c in 0 until tile.cols) {
                val cx = tile.x + c * slot
                val cy = gy + r * slot
                val over = tile.cached && pointerInList && hit(mouseX, mouseY, cx, cy, StorageTheme.ITEM, StorageTheme.ITEM)
                StorageTheme.cell(cx, cy, if (tile.cached) slotHover[key to r * tile.cols + c, over] else 0f)
            }

            when {
                live -> {}
                tile.cached -> for (cell in tile.cells) {
                    val cy = cell.y - scrollY
                    if (cell.matched) StorageTheme.match(cell.x, cy)
                    StorageTheme.item(cell.raw, cell.x, cy)
                    if (pointerInList && hit(mouseX, mouseY, cell.x, cy, StorageTheme.ITEM, StorageTheme.ITEM)) hovered = cell.raw
                }
                else -> Render.textCentered(
                    storageTr("click_to_load", "Click to load"), tile.x + tile.w / 2,
                    Render.centerY(gy, tile.rows * slot, Theme.FONT_SMALL, Fonts.medium),
                    Theme.FONT_SMALL, Theme.ACCENT, Fonts.medium,
                )
            }
        }
        return hovered
    }

    private fun renderHeader(tile: StorageLayout.Tile, ty: Int, mouseX: Int, mouseY: Int) {
        val key = tile.ref.key
        val headerH = StorageLayout.HEADER_H - 2
        if (renaming == key) {
            renameInput.render(tile.x.toFloat(), ty - 1f, tile.w - 2f, headerH.toFloat(), mouseX.toFloat(), mouseY.toFloat())
            return
        }
        // Blank custom names fall back to the label.
        val name = names[key]?.takeIf { it.isNotBlank() } ?: tile.ref.label
        val color = if (tile.cached) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED
        Render.text(name, tile.x, Render.centerY(ty - 2, headerH, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, color, Fonts.medium)
    }

    fun renderVanilla(d: ScreenDraw, ctx: Any, mouseX: Int, mouseY: Int): String? {
        val layout = built?.takeIf { tiles.isNotEmpty() } ?: return null
        val dim = StorageTheme.dim.rgb
        var hovered: String? = null
        d.pushScissor(ctx, areaX, top, areaW, bottom - top)
        for (tile in layout.tiles) {
            val gy = tile.gridY - scrollY
            if (tile.y - scrollY + tile.h < top || tile.y - scrollY > bottom) continue
            val body = liveBody
            if (tile.ref.key == activeKey && body != null) {
                for (i in body.indices) {
                    val raw = body[i]?.raw ?: continue
                    val cx = tile.x + (i % tile.cols) * StorageTheme.SLOT
                    val cy = gy + (i / tile.cols) * StorageTheme.SLOT
                    d.item(ctx, raw, cx, cy)
                    if (mouseY in top until bottom && hit(mouseX, mouseY, cx, cy, StorageTheme.ITEM, StorageTheme.ITEM)) hovered = raw
                }
            } else {
                for (cell in tile.cells) {
                    val cy = cell.y - scrollY
                    d.itemDecorations(ctx, cell.raw, cell.x, cy)
                    if (!cell.matched) d.fill(ctx, cell.x, cy, StorageTheme.ITEM, StorageTheme.ITEM, dim)
                }
            }
        }
        d.popScissor(ctx)
        return hovered
    }

    /**
     * Places the open page's real slots in this grid so clicks hit vanilla. Re-derived each frame since the menu gets
     * recreated per page. [navRows] and scrolled-out rows get parked off-screen.
     */
    private fun layoutLivePage(navRows: Int, cols: Int, gridX: Int, gridY: Int, pitch: Int) {
        val container = Platform.container
        val nav = navRows * cols
        val count = container.chestSlotCount()
        for (i in 0 until count) {
            val j = i - nav
            val y = gridY + (j / cols) * pitch
            // Hidden slots could sit under the search box or selector, where a hotbar key would swap an invisible item.
            if (i < nav || y < top || y + 16 > bottom) {
                container.parkChestSlot(i)
            } else {
                container.moveChestSlot(i, gridX + (j % cols) * pitch, y)
            }
        }
    }

    private fun parkLivePage() {
        val container = Platform.container
        for (i in 0 until container.chestSlotCount()) container.parkChestSlot(i)
    }

    fun grabScrollbar(mouse: MouseButtonInput): Boolean = scroll.mouseClicked(mouse)

    fun renameClicked(mouse: MouseButtonInput): Boolean = renaming != null && renameInput.mouseClicked(mouse)

    /** A tile opens its page, right-click on a header renames it. */
    fun mouseClicked(mouse: MouseButtonInput): Boolean {
        for (tile in tiles) {
            if (mouse.x < tile.x || mouse.x > tile.x + tile.w) continue
            val ty = tile.y - scrollY
            val gy = tile.gridY - scrollY
            if (mouse.y < ty || mouse.y > ty + tile.h) continue
            val active = tile.ref.key == activeKey
            if (mouse.y < gy) {
                if (mouse.isRight) openMenu(tile.ref.key, mouse.x, mouse.y)
                else if (!active) onOpen(tile.ref)
                return true
            }
            if (active) {
                /*
                 * Real slots are under these cells, let vanilla handle it. Re-park first, the server might have
                 * reopened the menu since the last render.
                 */
                layoutLivePage(1, tile.cols, tile.x, gy, StorageTheme.SLOT)
                return mouse.y < top || mouse.y > bottom // only within the visible list
            }
            onOpen(tile.ref)
            return true
        }
        return true
    }

    private fun openMenu(key: String, mouseX: Float, mouseY: Float) {
        UiManager.contextMenuOver(
            mouseX, mouseY,
            listOf(
                ContextMenu.Item(storageTr("rename", "Rename")) { startRename(key) },
                ContextMenu.Item(storageTr("reset_name", "Reset"), LucideIcons.rotateCcw, enabled = key in names) {
                    StorageCache.setName(key, null)
                },
            ),
        )
    }

    private fun startRename(key: String) {
        renaming = key
        renameInput.text = names[key] ?: ""
        renameInput.focused = true
    }

    private fun commitRename() {
        renaming?.let { StorageCache.setName(it, renameInput.text.trim()) }
        cancelRename()
    }

    fun mouseScrolled(wheel: MouseScrollInput): Boolean = scroll.mouseScrolled(wheel)

    fun mouseDragged(drag: MouseDragInput): Boolean = scroll.mouseDragged(drag) || renameInput.mouseDragged(drag)

    fun mouseReleased(mouse: MouseButtonInput) {
        scroll.mouseReleased(mouse)
        renameInput.mouseReleased(mouse)
    }

    /** Eats all keys while renaming. */
    fun keyPressed(key: KeyInput): Boolean {
        if (renaming == null) return false
        renameInput.keyPressed(key)
        return true
    }

    fun charTyped(codepoint: Int): Boolean {
        if (renaming == null) return false
        Character.toChars(codepoint).forEach { renameInput.charTyped(CharInput(it, 0)) }
        return true
    }

    /** Clicking off or Escape abandons it. */
    fun cancelRename() {
        renaming = null
        renameInput.focused = false
    }

    fun resetScroll() = scroll.reset()

    fun reset() {
        scroll.reset()
        revealedKey = null
        slotHover.clear()
        cancelRename()
    }
}
