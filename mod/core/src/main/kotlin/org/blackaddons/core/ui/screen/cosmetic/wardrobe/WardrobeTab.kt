package org.blackaddons.core.ui.screen.cosmetic.wardrobe

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.core.cosmetics.PlayerCosmetics
import org.blackaddons.core.cosmetics.TintHex
import org.blackaddons.core.cosmetics.me.Me3dService
import org.blackaddons.core.cosmetics.mesh.Cosmetic3dEntry
import org.blackaddons.core.ui.screen.cosmetic.renderStatusMessage
import java.awt.Color

private val tr = I18n.scope("blackaddons.wardrobe")

/**
 * 3D wardrobe shell: layout, footer and input routing over [WardrobeState], [WardrobeGrid], [WardrobeDraft] and
 * [WardrobeDetails]. The selection shows on your own model until close. Only u see it.
 */
internal class WardrobeTab(
    private val state: WardrobeState,
    private val openPopover: (Panel) -> Unit,
) {

    private var selection: Cosmetic3dEntry? = null

    private val draft = WardrobeDraft(state, onPreviewChanged = ::pushPreviews)
    val widgets = WidgetHost()
    private val grid = widgets.add(WardrobeGrid(onSelect = ::onTileSelected))
    private val details = widgets.add(WardrobeDetails(draft, onEditTint = ::editTint))

    private val actionButton = widgets.add(Button("") { runAction() }.apply { accent = true })
    private val retryButton = widgets.add(Button(I18n.trOr("blackaddons.cosmetic_editor.retry", "Retry")) { state.reload() })
        .apply { accent = true }

    fun onShow() {
        clearSelection()
        state.reload()
    }

    fun onClose() {
        // Read before clearing, because closing removes the draft.
        val worn = if (state.wroteAndReloaded) state.equippedItems() else null

        clearSelection()
        grid.close()
        PlayerCosmetics.previewItems = emptyMap()

        // Publish a saved change so it doesn't blink. Nothing written: clear nothing, the nick editor might hold its own save.
        worn?.let(PlayerCosmetics::ownItemsSaved)
    }

    fun render(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        state.applyPendingResults()

        if (state.phase != WardrobeState.Phase.READY) {
            grid.clearItems()
            details.clear()
            actionButton.clear()
            renderStatus(x, y, w, h, mouseX, mouseY)
            return
        }
        retryButton.clear()

        // Held by id, because reloads return new instances.
        selection = selection?.let { held -> state.entries.firstOrNull { it.id == held.id } }
        selection?.let { if (draft.needsSeeding(it)) draft.seed(it) }
        draft.applyPendingEdit(selection)

        if (grid.syncMannequins(state.entries)) pushPreviews()

        val footerH = 24f + Theme.PADDING
        details.layout(selection, w)
        val detailsH = details.height(h - footerH)
        val listH = h - footerH - detailsH - if (detailsH > 0f) Theme.PADDING else 0f

        grid.render(x, y, w, listH, state.entries, selection?.id, mouseX, mouseY)

        if (detailsH > 0f) {
            val detailsY = y + listH + Theme.PADDING
            Render.rect(x, detailsY - Theme.PADDING / 2f, w, 1f, Theme.BORDER.withAlpha(90), 0f)
            details.render(x, detailsY, w, detailsH, mouseX, mouseY)
        } else {
            details.clear()
        }

        renderFooter(x, y + h - 24f, w, mouseX, mouseY)
    }

    private fun renderStatus(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        val msg = when (state.phase) {
            WardrobeState.Phase.LOADING -> tr("loading", "Loading your wardrobe...")
            WardrobeState.Phase.NOT_LINKED -> I18n.trOr("blackaddons.cosmetic_editor.not_linked", "This account isn't linked to a cosmetic.")
            WardrobeState.Phase.ERROR -> I18n.trOr("blackaddons.cosmetic_editor.error", "Couldn't reach the server. Try again later.")
            WardrobeState.Phase.READY -> return
        }
        renderStatusMessage(x, y, w, h, msg, retryButton, state.phase == WardrobeState.Phase.ERROR, mouseX, mouseY)
    }

    private fun renderFooter(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        val (status, statusColor) = footerStatus()
        if (status.isNotEmpty()) {
            Render.text(
                status, x, Render.centerY(y, 24f, Theme.FONT_SMALL, Fonts.regular),
                Theme.FONT_SMALL, statusColor, Fonts.regular, maxWidth = w - 96f,
            )
        }

        val selected = selection
        actionButton.label = when {
            selected == null || !selected.equipped -> tr("equip", "Equip")
            draft.isDirty(selected) -> tr("apply", "Apply")
            else -> tr("take_off", "Take off")
        }
        actionButton.enabled = selected != null && selected.unlocked && !state.busy
        actionButton.render(x + w - 88f, y, 88f, 24f, mouseX, mouseY)
    }

    private fun footerStatus(): Pair<String, Color> {
        if (state.busy) return tr("saving", "Saving...") to Theme.TEXT_SECONDARY
        state.outcome?.let { return outcomeMessage(it) }
        val selected = selection ?: return "" to Theme.TEXT_MUTED
        if (!selected.unlocked) return tr("locked_hint", "You don't own this one.") to Theme.TEXT_MUTED
        if (!selected.equipped || draft.isDirty(selected)) {
            return tr("trying_on", "Trying it on") to Theme.TEXT_MUTED
        }
        return "" to Theme.TEXT_MUTED
    }

    private fun outcomeMessage(outcome: Me3dService.Write): Pair<String, Color> = when (outcome) {
        Me3dService.Write.Ok -> tr("saved", "Saved") to Theme.SUCCESS
        Me3dService.Write.Locked -> tr("locked_hint", "You don't own this one.") to Theme.DANGER
        Me3dService.Write.NotFound -> tr("gone", "That cosmetic is no longer available.") to Theme.DANGER
        // The server writes these in first person, so keep its message.
        is Me3dService.Write.TailCapeConflict ->
            (outcome.message ?: tr("tail_cape", "A tail and a cape can't be worn together.")) to Theme.DANGER
        is Me3dService.Write.Invalid ->
            (outcome.message ?: tr("invalid", "The server refused that change.")) to Theme.DANGER
        is Me3dService.Write.Error -> tr("failed", "Couldn't save — try again") to Theme.DANGER
    }


    // A slider gets the drag first, so the grid doesn't spin on it.
    private fun onTileSelected(entry: Cosmetic3dEntry) {
        if (entry.id == selection?.id) clearSelection() else select(entry)
        state.outcome = null
    }

    private fun editTint(slot: String, swatch: Rect) {
        val allowAlpha = draft.supportsAlpha(slot)
        // Fires on every drag frame. The draft rebuilds once per frame and still draws under the popover.
        openPopover(
            ColorPopover(
                draft.tintColor(slot),
                allowAlpha = allowAlpha,
                anchorX = swatch.x, anchorY = swatch.y, anchorW = swatch.w, anchorH = swatch.h,
            ) { picked -> draft.setTint(slot, TintHex.format(picked.rgb, allowAlpha)) },
        )
    }

    private fun runAction() {
        val selected = selection ?: return
        if (!selected.unlocked) return
        if (selected.equipped && !draft.isDirty(selected)) {
            state.unequip(selected)
            clearSelection()
            return
        }
        // The preview stays until the server's answer arrives.
        state.equip(selected.id, draft.tints, draft.modifiers, draft.adjust)
    }

    private fun select(entry: Cosmetic3dEntry) {
        selection = entry
        details.onSelectionChanged()
        draft.seed(entry)
    }

    private fun clearSelection() {
        selection = null
        details.onSelectionChanged()
        draft.clear()
        pushPreviews()
    }

    private fun pushPreviews() {
        val self = Platform.player.uuid
        val preview = draft.selfPreview
        PlayerCosmetics.previewItems =
            if (self == null || preview == null) grid.mannequins else grid.mannequins + (self to preview)
    }
}
