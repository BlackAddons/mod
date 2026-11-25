package org.blackaddons.extras.features.impl.extras.storage

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.ui.anim.AnimationMap
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.MouseButtonInput

/**
 * NEU's bottom-left selector: 9 ender chest pages, then 18 backpack slots. Only openable cells are clickable. While a
 * page is open, "Edit" goes back to the overview.
 */
internal class StorageSelector {

    /** Openable pages only. */
    private data class Cell(val x: Int, val y: Int, val ref: StorageRef)

    private var cells: List<Cell> = emptyList()
    private val hover = AnimationMap<String>()

    private val edit = Button("") { Platform.chat.sendCommand("storage") }
    private var editShown = false

    /** Returns the hovered icon's raw form. */
    fun render(metrics: ContainerMetrics, mouseX: Int, mouseY: Int, pageOpen: Boolean): String? {
        val refs = StorageCache.selectorRefs()
        editShown = false
        if (refs.isEmpty()) {
            cells = emptyList()
            return null
        }
        val byPage = refs.associateBy { it.type to it.page }

        // Hug the inventory's left edge, clamped to the margin.
        val left = (metrics.inventoryLeft - 8 - EC_PAGES * SEL_CELL).coerceAtLeast(StorageTheme.MARGIN)
        val top = metrics.inventoryBottom - SEL_HEIGHT
        val found = ArrayList<Cell>(EC_PAGES + BP_SLOTS)
        var hovered: String? = null

        // Icon on a slot background. Each captured ref is a registered real slot, so the click handler opens or relays.
        fun cell(x: Int, y: Int, iconRaw: String?, ref: StorageRef?) {
            val over = hit(mouseX, mouseY, x, y, StorageTheme.ITEM, StorageTheme.ITEM)
            StorageTheme.cell(x, y, if (ref != null) hover[ref.key, over] else 0f)
            if (ref != null) found += Cell(x, y, ref)
            if (iconRaw != null) StorageTheme.item(iconRaw, x, y)
            // Real items only, not placeholder panes.
            if (over && iconRaw != null && iconRaw == ref?.icon) hovered = iconRaw
        }

        val labelRoom = if (pageOpen) renderEditButton(left, top, mouseX, mouseY) - left - 4f else ROW_W.toFloat()
        label(storageTr("ender_chest_pages", "Ender Chest Pages"), left, top, labelRoom)

        val ecRowY = top + HEADER_H
        for (p in 1..EC_PAGES) {
            // Unlocked pages show Hypixel's icon, unlisted ones are locked (red pane).
            val ref = byPage[ContainerType.ENDER_CHEST to p]
            val icon = if (ref == null) ICON_LOCKED_EC else ref.icon ?: ICON_DEFAULT_EC
            cell(left + (p - 1) * SEL_CELL, ecRowY, icon, ref)
        }

        val bpHeaderY = ecRowY + SEL_CELL + 4
        label(storageTr("storage_pages", "Storage Pages"), left, bpHeaderY)
        val bpRowY = bpHeaderY + HEADER_H
        for (s in 1..BP_SLOTS) {
            val ref = byPage[ContainerType.BACKPACK to s]
            val icon = when (ref?.state) {
                SlotState.OPEN -> ref.icon
                SlotState.EMPTY -> ICON_EMPTY_BP
                SlotState.LOCKED -> ICON_LOCKED_BP
                null -> null // slot the player doesn't have
            }
            cell(left + ((s - 1) % 9) * SEL_CELL, bpRowY + ((s - 1) / 9) * SEL_CELL, icon, ref)
        }

        cells = found
        return hovered
    }

    private fun label(text: String, x: Int, y: Int, maxW: Float = ROW_W.toFloat()) =
        Render.text(text, x, Render.centerY(y, LABEL_H, TextStyle.SMALL_SECONDARY_MEDIUM), TextStyle.SMALL_SECONDARY_MEDIUM, maxWidth = maxW)

    private fun renderEditButton(left: Int, top: Int, mouseX: Int, mouseY: Int): Float {
        edit.label = storageTr("edit", "Edit")
        val w = edit.width
        val x = left + ROW_W - 2 - w
        edit.render(x, top.toFloat(), w, LABEL_H.toFloat(), mouseX.toFloat(), mouseY.toFloat())
        editShown = true
        return x
    }

    fun clickedEdit(mouse: MouseButtonInput): Boolean = editShown && edit.mouseClicked(mouse)

    fun refAt(mouseX: Float, mouseY: Float): StorageRef? =
        cells.firstOrNull {
            hit(mouseX.toInt(), mouseY.toInt(), it.x, it.y, 16, 16)
        }?.ref

    private companion object {
        // 9 ender chest pages in one row, 18 backpack slots in two.
        const val SEL_CELL = 18
        const val EC_PAGES = 9
        const val BP_SLOTS = 18
        const val HEADER_H = 16
        const val LABEL_H = HEADER_H - 4
        const val ROW_W = EC_PAGES * SEL_CELL
        const val SEL_HEIGHT = HEADER_H + SEL_CELL + 4 + HEADER_H + SEL_CELL * 2

        // Panes like Hypixel's, for a locked EC page or empty/locked backpacks. ItemStack.CODEC JSON.
        const val ICON_LOCKED_EC = """{"id":"minecraft:red_stained_glass_pane","count":1}"""
        const val ICON_DEFAULT_EC = """{"id":"minecraft:purple_stained_glass_pane","count":1}"""
        const val ICON_EMPTY_BP = """{"id":"minecraft:brown_stained_glass_pane","count":1}"""
        const val ICON_LOCKED_BP = """{"id":"minecraft:gray_dye","count":1}"""
    }
}
