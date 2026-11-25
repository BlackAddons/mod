package org.blackaddons.extras.features.impl.extras.storage

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.gui.ContainerOverlay
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput

/**
 * Storage browser [ContainerOverlay], reimplemented from NotEnoughUpdates' `StorageOverlay` (LGPL, behavior only).
 * Like NEU it hides the vanilla GUI and draws the whole window; vanilla draws live items, slots and tooltips on top.
 */
internal object StoragePanel : ContainerOverlay {

    var active: () -> Boolean = { true }

    var pagesPerRow: () -> Int = { 3 }

    private val tileList = StorageTileList(onOpen = { open(it) })
    private val searchBox = StorageSearchBox(onChange = { tileList.resetScroll() })
    private val selector = StorageSelector()

    private var hovered: String? = null

    /** Null on the overview. */
    private var activeKey: String? = null

    // Overview and every page (a dark reskin, real slots stay native).
    override fun appliesTo(title: String): Boolean =
        active() && (StoragePatterns.isStorageOverview(title) || StoragePatterns.matchPage(title) != null)

    override fun repositionInventory(title: String): Boolean = true

    /** The open page as a [CapturedContainer], for live items and click relay. Null on the overview. */
    private fun buildLivePage(): CapturedContainer? {
        livePage?.let { return it }

        val title = Platform.container.title() ?: return null
        val page = StoragePatterns.matchPage(title) ?: return null
        val caps = Platform.container.capture() ?: return null
        val body = caps.raw.drop(caps.cols) // drop Hypixel's nav row
        val items = body.map { raw -> raw?.let { CapturedItem(it, Platform.items.describe(it)?.name ?: "", 1) } }
        return CapturedContainer(page.type, page.page, title, (caps.rows - 1).coerceAtLeast(0), caps.cols, items, 0L)
            .also { livePage = it }
    }

    /** Cached per tick: reading means serializing and decoding every slot, and contents only change on a packet. */
    private var livePage: CapturedContainer? = null

    /** Called each client tick. */
    fun invalidateLivePage() {
        livePage = null
    }

    override fun renderUi(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        // The open page's tile draws live items and relays clicks.
        val live = buildLivePage()
        activeKey = live?.key

        // The platform mixin hides the vanilla GUI, so this only dims the world.
        Render.glass(0, 0, metrics.width, metrics.height, Glass.BACKDROP)

        val tileHover = tileList.renderUi(metrics, searchBox.text, activeKey, live, mouseX, mouseY)
        searchBox.render(metrics, mouseX, mouseY)
        val selHover = selector.render(metrics, mouseX, mouseY, pageOpen = activeKey != null)
        hovered = tileHover ?: selHover
    }

    override fun render(ctx: Any, metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        val d = Platform.screenDraw
        val liveHover = tileList.renderVanilla(d, ctx, mouseX, mouseY)
        // Inventory before the tooltip, the tooltip draws last.
        d.renderInventoryDark(ctx, mouseX, mouseY)
        (liveHover ?: hovered)?.let { d.tooltip(ctx, it, mouseX, mouseY) }
    }

    override fun mouseClicked(metrics: ContainerMetrics, mouse: MouseButtonInput): Boolean {
        // Real inventory clicks just go to vanilla.
        if (mouse.x >= metrics.inventoryLeft && mouse.x < metrics.inventoryRight &&
            mouse.y >= metrics.inventoryTop && mouse.y < metrics.inventoryBottom
        ) {
            return false
        }

        // "Edit" goes back to the overview, where selector actions work.
        if (selector.clickedEdit(mouse)) return true

        if (tileList.grabScrollbar(mouse)) return true

        if (tileList.renameClicked(mouse)) return true
        tileList.cancelRename()
        // Clicking anywhere else drops search focus.
        if (searchBox.mouseClicked(mouse)) return true

        selector.refAt(mouse.x, mouse.y)?.let { ref ->
            clickSelector(ref, mouse)
            return true
        }
        return tileList.mouseClicked(mouse)
    }

    /** Add/remove/icon relays need a live overview (else the index hits the open page). Navigation works anywhere. */
    private fun clickSelector(ref: StorageRef, mouse: MouseButtonInput) {
        val overviewLive = activeKey == null
        when {
            // Right-click: EC -> icon menu, backpack -> remove.
            mouse.isRight && overviewLive && ref.slot >= 0 -> Platform.container.clickSlot(ref.slot, 1, false)
            ref.openable -> open(ref)
            // Empty/locked: relay, so a backpack on the cursor gets added.
            overviewLive && ref.slot >= 0 -> Platform.container.clickSlot(ref.slot, 0, false)
        }
    }

    override fun mouseScrolled(metrics: ContainerMetrics, wheel: MouseScrollInput): Boolean = tileList.mouseScrolled(wheel)

    override fun mouseDragged(metrics: ContainerMetrics, drag: MouseDragInput): Boolean =
        tileList.mouseDragged(drag) || searchBox.mouseDragged(drag)

    override fun mouseReleased(mouse: MouseButtonInput) {
        tileList.mouseReleased(mouse)
        searchBox.mouseReleased(mouse)
    }

    // Rename, then search, each eating all keys while focused so vanilla hotkeys don't fire.
    override fun keyPressed(metrics: ContainerMetrics, key: KeyInput): Boolean =
        tileList.keyPressed(key) || searchBox.keyPressed(key)

    override fun charTyped(codepoint: Int): Boolean = tileList.charTyped(codepoint) || searchBox.charTyped(codepoint)

    private fun open(ref: StorageRef) {
        Platform.chat.sendCommand(ref.command)
    }

    /** Called when storage closes or the world changes. */
    fun reset() {
        searchBox.reset()
        tileList.reset()
    }
}
