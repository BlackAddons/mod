package org.blackaddons.core.ui.screen.pv.parts
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.profile.RecentPlayers

internal class RecentPlayersColumn(private val onOpen: (RecentPlayers.Entry) -> Unit) : InputTarget {

    private class Slot(val entry: RecentPlayers.Entry, val x: Float, val y: Float, val size: Float)
    private var slots = emptyList<Slot>()

    fun render(panelX: Float, panelY: Float, panelW: Float, panelH: Float, mouseX: Float, mouseY: Float) {
        val entries = chips()
        if (entries.isEmpty()) {
            slots = emptyList()
            return
        }

        // Chips shrink to fit. Below their minimum, the oldest get dropped. Your own head is first, so it stays.
        val available = panelH - INSET * 2f
        val chip = (available / entries.size - GAP).coerceIn(MIN_CHIP, CHIP)
        val shown = entries.take((available / (chip + GAP)).toInt().coerceAtLeast(0))

        val x = panelX + panelW + MARGIN
        var y = panelY + INSET
        slots = shown.map { entry ->
            Slot(entry, x, y, chip).also { y += chip + GAP }
        }

        for (slot in slots) drawChip(slot, mouseX, mouseY)
    }

    private fun chips(): List<RecentPlayers.Entry> {
        val recent = RecentPlayers.list()
        val self = selfEntry(recent)
        val selfId = self?.let { Uuids.parse(it.uuid) }
        val others = recent.filterNot { selfId != null && Uuids.parse(it.uuid) == selfId }
        return listOfNotNull(self) + others.take(OTHERS)
    }

    private fun selfEntry(recent: List<RecentPlayers.Entry>): RecentPlayers.Entry? {
        val uuid = Platform.player.uuid ?: return null
        val name = Platform.player.name ?: return null
        // Live tab-list skin first (current after a skin change), then the recorded one, otherwise the default.
        val cached = recent.firstOrNull { Uuids.parse(it.uuid) == uuid }?.skin
        return RecentPlayers.Entry(uuid.toString(), name, Platform.player.skinTextures ?: cached)
    }

    private fun drawChip(slot: Slot, mouseX: Float, mouseY: Float) {
        val hovered = contains(slot, mouseX, mouseY)
        Render.rect(slot.x, slot.y, slot.size, slot.size, if (hovered) Theme.SURFACE_HOVER else Theme.SURFACE, Theme.RADIUS)

        val headSize = slot.size - PAD * 2f
        val e = slot.entry
        Render.playerHead(e.uuid, e.name, e.skin, slot.x + PAD, slot.y + PAD, headSize, headSize)

        if (hovered) Tooltip.show(slot.entry.name)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        val slot = slots.firstOrNull { contains(it, mouse.x, mouse.y) } ?: return false
        onOpen(slot.entry)
        return true
    }

    private fun contains(slot: Slot, mx: Float, my: Float) =
        hit(mx, my, slot.x, slot.y, slot.size, slot.size)

    private companion object {
        const val OTHERS = 9
        const val CHIP = 34f
        const val MIN_CHIP = 22f
        const val PAD = 3f
        const val GAP = 6f
        const val MARGIN = 4f
        const val INSET = 6f
    }
}
