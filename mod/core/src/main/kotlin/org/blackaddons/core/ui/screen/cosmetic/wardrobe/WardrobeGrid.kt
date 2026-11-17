package org.blackaddons.core.ui.screen.cosmetic.wardrobe

import org.blackaddons.api.Platform
import org.blackaddons.api.cosmetics.Cosmetic3dItem
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.util.I18n
import org.blackaddons.core.cosmetics.mesh.Cosmetic3dEntry
import org.blackaddons.core.cosmetics.mesh.CosmeticModifiers
import java.util.UUID
import kotlin.math.abs

private val tr = I18n.scope("blackaddons.wardrobe")

/**
 * Catalog grid, one mannequin per tile. Locked cosmetics show as a showcase. Dragging a tile spins it. A press that
 * didn't move selects on release.
 */
internal class WardrobeGrid(private val onSelect: (Cosmetic3dEntry) -> Unit) : InputTarget {

    var mannequins: Map<UUID, List<Cosmetic3dItem>> = emptyMap()
        private set

    private var mannequinsFor: List<Cosmetic3dEntry>? = null

    /** Turntable angle per tile, by cosmetic id. A tile u turned stays turned. */
    private val tileYaw = HashMap<String, Float>()
    private val tilePitch = HashMap<String, Float>()

    // Pressed tile and travel since. Real clicks slip a pixel or two.
    private var pressedTile: Cosmetic3dEntry? = null
    private var dragTravel = 0f

    private var listX = 0f
    private var tileW = TILE_MIN_W

    // For row callbacks, because ScrollableList doesn't pass the pointer.
    private var pointerX = 0f
    private var pointerY = 0f

    private var selectedId: String? = null

    private val list = ScrollableList<List<Cosmetic3dEntry>>().apply {
        // Row height includes the gap, so spacing is uniform.
        rowHeight = TILE_H
        rowGap = 0f
        contentInsetY = ScrollableList.SIDE_INSET
        emptyMessage = tr("empty", "Nothing in your wardrobe yet.")
        drawRow = ::drawTileRow
    }

    fun syncMannequins(entries: List<Cosmetic3dEntry>): Boolean {
        if (entries === mannequinsFor) return false
        mannequinsFor = entries
        mannequins = entries.mapNotNull { entry ->
            resolveStored(entry)?.let { mannequinUuid(entry) to listOf(it) }
        }.toMap()
        return true
    }

    fun render(
        x: Float, y: Float, w: Float, h: Float,
        entries: List<Cosmetic3dEntry>, selectedId: String?,
        mouseX: Float, mouseY: Float,
    ) {
        pointerX = mouseX
        pointerY = mouseY
        this.selectedId = selectedId

        // Last gap added back, so the last tile ends at the same inset.
        val rowW = w - ScrollableList.SIDE_INSET * 2f
        val columns = ((rowW + TILE_GAP) / TILE_MIN_W).toInt().coerceAtLeast(1)
        tileW = (rowW + TILE_GAP) / columns
        list.items = entries.chunked(columns)
        list.boxWidth = w
        list.boxHeight = h
        list.render(x, y, w, h)
    }

    fun clearItems() {
        list.items = emptyList()
    }

    /** Forgets the mannequins with the screen, because nothing asks for their previews after that. */
    fun close() {
        mannequins = emptyMap()
        mannequinsFor = null
        pressedTile = null
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft) return false
        pressedTile = tileAt(mouse.x, mouse.y) ?: return false
        dragTravel = 0f
        return true
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        val id = pressedTile?.id ?: return false
        dragTravel += abs(drag.dx) + abs(drag.dy)
        if (dragTravel < DRAG_THRESHOLD) return true

        tileYaw[id] = (tileYaw[id] ?: 0f) - drag.dx * TILE_DRAG_SENS
        tilePitch[id] = ((tilePitch[id] ?: 0f) - drag.dy * TILE_DRAG_SENS)
            .coerceIn(-TILE_PITCH_LIMIT, TILE_PITCH_LIMIT)
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        val released = pressedTile
        pressedTile = null
        if (released == null || dragTravel >= DRAG_THRESHOLD) return false
        onSelect(released)
        return true
    }

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean =
        list.mouseScrolled(wheel)

    private fun tileAt(mouseX: Float, mouseY: Float): Cosmetic3dEntry? {
        val index = list.rowAt(mouseX, mouseY)
        val row = list.items.getOrNull(index) ?: return null
        val offset = mouseX - listX
        if (offset < 0f) return null
        val column = (offset / tileW).toInt()
        if (offset - column * tileW > tileW - TILE_GAP) return null
        if (mouseY - list.rowTop(index) > TILE_H - TILE_GAP) return null
        return row.getOrNull(column)
    }

    private fun drawTileRow(row: List<Cosmetic3dEntry>, index: Int, rowX: Float, rowY: Float, rowW: Float) {
        // The hit test measures from where the row was drawn.
        listX = rowX
        for ((i, entry) in row.withIndex()) drawTile(entry, rowX + i * tileW, rowY)
    }

    private fun drawTile(entry: Cosmetic3dEntry, x: Float, y: Float) {
        val w = tileW - TILE_GAP
        val h = TILE_H - TILE_GAP
        val selected = entry.id == selectedId
        // The list's scissor clips hover too.
        val hovered = hit(pointerX, pointerY, x, y, w, h)

        Render.rect(x, y, w, h, if (selected) Theme.SURFACE_HOVER else Theme.SURFACE.withAlpha(if (hovered) 150 else 90), Theme.RADIUS_SMALL)
        Render.hollowRect(x, y, w, h, 1f, if (selected) Theme.ACCENT else Theme.BORDER.withAlpha(90), Theme.RADIUS_SMALL)

        // Framed on where the cosmetic sits, so a tile of ears shows ears.
        val modelH = h - TILE_LABEL_H
        Platform.playerPreview?.requestPlayer(
            uuid = mannequinUuid(entry).toString(),
            name = MANNEQUIN_NAME,
            textures = null,
            signature = null,
            x = x + 1f, y = y + 1f, w = w - 2f, h = modelH,
            yawDeg = tileYaw[entry.id] ?: 0f,
            pitchDeg = tilePitch[entry.id] ?: 0f,
            key = entry.id,
            focusOnCosmetic = true,
            skinTexture = MANNEQUIN_SKIN,
        )

        Render.textCentered(
            entry.name, x + w / 2f, y + modelH + 2f, Theme.FONT_SMALL,
            if (entry.unlocked) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED, Fonts.medium,
            maxWidth = w - 8f,
        )

        val badge = when {
            entry.equipped -> tr("worn", "Worn") to Theme.SUCCESS
            !entry.unlocked -> tr("locked", "Locked") to Theme.TEXT_MUTED
            else -> null
        }
        badge?.let { (text, color) -> Render.text(text, x + 4f, y + 3f, Theme.FONT_SMALL, color, Fonts.regular) }
    }

    private fun resolveStored(entry: Cosmetic3dEntry) =
        CosmeticModifiers.previewItem(entry, entry.modifiers, entry.tints, entry.adjust)

    private companion object {
        const val TILE_MIN_W = 92f
        const val TILE_H = 104f
        const val TILE_GAP = 6f

        const val TILE_LABEL_H = 14f

        const val TILE_DRAG_SENS = 0.6f
        const val TILE_PITCH_LIMIT = 30f

        const val DRAG_THRESHOLD = 4f

        /** The mannequins' profile name. Never shown, but a fake player needs one. */
        const val MANNEQUIN_NAME = "Mannequin"

        /** The body every catalog tile wears, so all cosmetics get judged on one backdrop. */
        const val MANNEQUIN_SKIN = "assets/blackaddons/textures/entity/mannequin.png"

        /** Stable fake UUID per cosmetic. The renderer keys by (uuid, item). */
        fun mannequinUuid(entry: Cosmetic3dEntry): UUID =
            UUID.nameUUIDFromBytes("blackaddons:mannequin:${entry.id}".toByteArray())
    }
}
