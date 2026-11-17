package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.render.CosmeticName
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.TabStrip
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.cosmetics.me.CosmeticDraft
import org.blackaddons.core.cosmetics.me.MeCosmeticService
import org.blackaddons.core.ui.screen.cosmetic.CosmeticEditorState.Phase
import org.blackaddons.core.ui.screen.cosmetic.wardrobe.WardrobeState
import org.blackaddons.core.ui.screen.cosmetic.wardrobe.WardrobeTab
import java.awt.Color

private val tr = I18n.scope("blackaddons.cosmetic_editor")

/**
 * In-game editor for the player's cosmetic (`/v1/me/cosmetic`): live name/model preview, color and scale, explicit
 * Save. Name requests are separate (applied or reviewed). The server keeps `enabled` and ownership. This is the shell.
 * Parts: [CosmeticEditorState], [ModelPreviewBox], [ColorModeEditor], [ScaleEditor], [WardrobeTab].
 */
internal class CosmeticEditorScreen : Panel() {

    // Sized like the Profile Viewer. Minimums are from the old fixed card.
    private var panelX = 0f
    private var panelY = 0f
    private var panelW = 0f
    private var panelH = 0f

    private val entrance = Animation(durationMs = Theme.ANIM_NORMAL.toLong())

    private val state = CosmeticEditorState()
    private val model = widgets.add(ModelPreviewBox())
    private val colors = widgets.add(ColorModeEditor(draft = ::readyDraft, openPopover = UiManager::push, onEdit = ::onEdit))
    private val styles = widgets.add(NickStyleEditor(draft = ::readyDraft, onEdit = ::onEdit))
    private val font = widgets.add(NickFontEditor(draft = ::readyDraft, openPopover = UiManager::push))
    private val scale = widgets.add(ScaleEditor(draft = ::readyDraft, onEdit = ::onEdit))

    // The controls column outgrows short screens, last frame's height sizes the scroll.
    private val controlsScroll = widgets.add(Scroll())
    private var controlsHeight = 0f

    private enum class Tab(val id: String) {
        NAME("name"),
        WARDROBE("wardrobe");

        companion object {
            fun of(id: String) = entries.first { it.id == id }
        }
    }

    private var tab = Tab.NAME
    private val wardrobe = WardrobeTab(WardrobeState(), openPopover = UiManager::push).also { widgets.add(it.widgets) }

    // On the panel's top edge, so tabs cost no body height.
    private val tabBar = widgets.add(TabStrip(
        tabs = listOf(
            TabStrip.Tab(Tab.NAME.id, tr("tab_name", "Name & size")),
            TabStrip.Tab(Tab.WARDROBE.id, tr("tab_wardrobe", "Wardrobe")),
        ),
        activeId = Tab.NAME.id,
        onSelect = { switchTo(Tab.of(it)) },
    ))

    private val saveButton = widgets.add(Button(tr("save", "Save")) { state.save() })
        .apply { accent = true }
    private val revertButton = widgets.add(Button(tr("revert", "Revert")) { state.revert() })
    private val retryButton = widgets.add(Button(tr("retry", "Retry")) { state.reload() })
        .apply { accent = true }

    private val nameInput = widgets.add(TextInput(placeholder = tr("name_placeholder", "Display name"), maxLength = 64)
        .apply { onEnter = { askForName() } })
    private val nameButton = widgets.add(Button(tr("name_change", "Change")) { askForName() })
    private var namePrimed = false
    @Volatile private var nameAsking = false
    @Volatile private var nameResult: MeCosmeticService.NameChange? = null

    init {
        keys.on(Keys.ESCAPE) { UiManager.pop() }
    }

    override fun onShow() = state.reload()

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        state.applyPendingResults()

        val screenW = UiManager.screenWidth
        val screenH = UiManager.screenHeight
        entrance.animateTo(1f)
        val progress = entrance.get()
        Render.rect(0f, 0f, screenW, screenH, Theme.OVERLAY_DIM.withAlpha((Theme.OVERLAY_DIM.alpha * progress).toInt()))

        panelW = minOf(maxOf(screenW * SCREEN_FRACTION_W, MIN_WIDTH), screenW - EDGE_PAD * 2f)
        panelH = minOf(maxOf(screenH * SCREEN_FRACTION_H, MIN_HEIGHT), screenH - TOP_CHROME * 2f)
        panelX = (screenW - panelW) / 2f
        panelY = (screenH - panelH) / 2f

        // Every path returns early, and the alpha has to be restored.
        Render.globalAlpha(progress)
        try {
            renderCard(mouseX, mouseY)
        } finally {
            Render.globalAlpha(1f)
        }
    }

    private fun renderCard(mouseX: Float, mouseY: Float) {
        val px = panelX
        val py = panelY
        val cardW = panelW
        val cardH = panelH

        // Tabs after the panel, because the accent underline sits across the edge.
        Render.rect(px, py, cardW, cardH, Theme.BACKGROUND, Theme.RADIUS_LARGE)
        Render.text(
            tr("title", "Cosmetics"),
            px + Theme.PADDING_LARGE, py + Theme.PADDING_LARGE,
            TextStyle.TITLE_PRIMARY_BOLD,
        )
        tabBar.render(px, py, mouseX, mouseY)
        val contentTop = py + Theme.PADDING_LARGE + Theme.FONT_TITLE + Theme.PADDING_LARGE

        val previewW = (cardW * PREVIEW_FRACTION).coerceIn(MIN_PREVIEW_W, MAX_PREVIEW_W)
        val previewX = px + Theme.PADDING_LARGE
        val bodyH = py + cardH - Theme.PADDING_LARGE - contentTop
        val ctlX = previewX + previewW + Theme.PADDING_LARGE
        val ctlW = px + cardW - Theme.PADDING_LARGE - ctlX

        // Separate resource: a player can own 3D items without a name record.
        if (tab == Tab.WARDROBE) {
            saveButton.clear()
            revertButton.clear()
            retryButton.clear()
            val d = state.draft
            // Same height as the name tab, so the frame doesn't jump.
            model.render(previewX, contentTop, previewW, bodyH - FOOTER_H, d?.scaleX ?: 1f, d?.scaleY ?: 1f, d?.scaleZ ?: 1f)
            wardrobe.render(ctlX, contentTop, ctlW, bodyH, mouseX, mouseY)
            return
        }

        val d = state.draft
        if (state.phase != Phase.READY || d == null) {
            saveButton.clear()
            revertButton.clear()
            renderStatusOnly(px, contentTop, cardW, cardH - (contentTop - py), mouseX, mouseY)
            return
        }
        retryButton.clear()

        renderPreview(previewX, contentTop, previewW, bodyH - FOOTER_H, d)
        // The view is wider than the column by the slider knob's overhang, or the scissor halves a knob at either end.
        val controlsW = if (controlsScroll.maxScroll > 0f) ctlW - SCROLL_GUTTER else ctlW
        controlsScroll.render(ctlX - KNOB_BLEED, contentTop, ctlW + KNOB_BLEED * 2f, bodyH - FOOTER_H, controlsHeight) { offset ->
            val top = contentTop - offset
            controlsHeight = renderControls(ctlX, top, controlsW, d, mouseX, mouseY) - top + KNOB_BLEED
        }
        renderFooter(px, py, cardW, cardH, mouseX, mouseY)
    }

    private fun renderStatusOnly(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        val msg = when (state.phase) {
            Phase.LOADING -> tr("loading", "Loading...")
            Phase.NOT_OWNED -> tr("not_owned", "You don't own a cosmetic yet.")
            Phase.NOT_LINKED -> tr("not_linked", "This account isn't linked to a cosmetic.")
            Phase.ERROR -> tr("error", "Couldn't reach the server. Try again later.")
            Phase.READY -> return
        }
        renderStatusMessage(x, y, w, h, msg, retryButton, state.phase == Phase.ERROR, mouseX, mouseY)
    }

    private fun renderPreview(x: Float, y: Float, w: Float, h: Float, d: CosmeticDraft) {
        val cosmetic = d.toPreviewCosmetic()
        val fallback = Platform.player.name ?: "Player"
        val nameSize = 16f
        val nameX = x + (w - CosmeticName.width(cosmetic, fallback, nameSize, Fonts.minecraft)) / 2f
        val nameY = y + 8f
        CosmeticName.draw(cosmetic, fallback, nameX, nameY, nameSize, Fonts.minecraft, Theme.TEXT_PRIMARY)

        // Only the disabled state gets a notice. Its strip comes out of the model box.
        val boxY = nameY + nameSize + 10f
        val noticeH = if (d.enabled) 0f else Render.textHeight(Theme.FONT_SMALL, Fonts.regular) + NOTICE_GAP
        val boxH = (y + h) - boxY - noticeH
        model.render(x, boxY, w, boxH, d.scaleX, d.scaleY, d.scaleZ)

        if (!d.enabled) {
            Render.text(
                tr("disabled", "Disabled by admin"),
                x, boxY + boxH + 2f, Theme.FONT_SMALL, Theme.DANGER, Fonts.regular,
            )
        }
    }

    private fun renderControls(x: Float, y: Float, w: Float, d: CosmeticDraft, mouseX: Float, mouseY: Float): Float {
        val below = colors.render(x, renderName(x, y, w, d, mouseX, mouseY), w, d, mouseX, mouseY)
        val styled = styles.render(x, below + Theme.PADDING_LARGE, w, d, mouseX, mouseY)
        val fonted = font.render(x, styled + Theme.PADDING_LARGE, w, d, mouseX, mouseY)
        return scale.render(x, fonted + Theme.PADDING_LARGE, w, d)
    }

    private fun renderName(x: Float, y: Float, w: Float, d: CosmeticDraft, mouseX: Float, mouseY: Float): Float {
        if (!namePrimed) {
            nameInput.text = d.displayName ?: ""
            namePrimed = true
        }
        nameResult?.let { result ->
            if (result is MeCosmeticService.NameChange.Applied) state.nameApplied(result.name)
        }

        val buttonW = nameButton.naturalWidth().coerceAtLeast(NAME_BUTTON_MIN_W)
        val fieldW = w - buttonW - Theme.PADDING
        nameInput.render(x, y, fieldW, NAME_H, mouseX, mouseY)
        nameButton.enabled = !nameAsking && nameInput.text.isNotBlank() && nameInput.text.trim() != d.displayName
        nameButton.render(x + fieldW + Theme.PADDING, y, buttonW, NAME_H, mouseX, mouseY)

        val (status, color) = when (val result = nameResult) {
            null -> if (nameAsking) tr("name_asking", "Asking...") to Theme.TEXT_MUTED
            else tr("name_hint", "A new name may go to staff for review first") to Theme.TEXT_MUTED
            is MeCosmeticService.NameChange.Applied -> tr("name_applied", "Name changed") to Theme.SUCCESS
            is MeCosmeticService.NameChange.Pending ->
                tr("name_pending", "Sent for review - see ticket #%d").format(result.ticketId) to Theme.TEXT_SECONDARY
            is MeCosmeticService.NameChange.Refused ->
                (result.message ?: tr("name_refused", "The name was not changed")) to Theme.DANGER
        }
        val statusY = y + NAME_H + 3f
        Render.text(status, x, statusY, Theme.FONT_SMALL, color, Fonts.regular, maxWidth = w)
        return statusY + Render.textHeight(Theme.FONT_SMALL, Fonts.regular) + Theme.PADDING_LARGE
    }

    private fun askForName() {
        val name = nameInput.text.trim()
        if (nameAsking || name.isEmpty() || name == readyDraft()?.displayName) return
        nameAsking = true
        nameResult = null
        OffThread.run(
            name = "cosmetic-name",
            work = { MeCosmeticService.changeName(name) },
            onFailure = { MeCosmeticService.NameChange.Refused(null) },
            onResult = { result ->
                nameResult = result
                nameAsking = false
            },
        )
    }

    private fun renderFooter(px: Float, py: Float, cardW: Float, cardH: Float, mouseX: Float, mouseY: Float) {
        val status = when {
            state.saving -> tr("saving", "Saving...") to Theme.TEXT_SECONDARY
            state.saveOutcome != null -> outcomeMessage(state.saveOutcome!!)
            state.dirty -> tr("unsaved", "Unsaved changes") to Theme.TEXT_MUTED
            else -> "" to Theme.TEXT_MUTED
        }
        val btnW = 80f; val btnH = 24f
        val canEdit = state.phase == Phase.READY && state.dirty && !state.saving
        val saveX = px + cardW - Theme.PADDING_LARGE - btnW
        val saveY = py + cardH - Theme.PADDING_LARGE - btnH
        Render.text(
            status.first, px + Theme.PADDING_LARGE, Render.centerY(saveY, btnH, Theme.FONT_SMALL, Fonts.regular),
            Theme.FONT_SMALL, status.second, Fonts.regular,
        )
        saveButton.enabled = canEdit
        saveButton.render(saveX, saveY, btnW, btnH, mouseX, mouseY)
        revertButton.enabled = canEdit
        revertButton.render(saveX - Theme.PADDING - btnW, saveY, btnW, btnH, mouseX, mouseY)
    }

    private fun outcomeMessage(outcome: MeCosmeticService.Save): Pair<String, Color> = when (outcome) {
        MeCosmeticService.Save.Ok -> tr("saved", "Saved") to Theme.SUCCESS
        MeCosmeticService.Save.Disabled -> tr("save_disabled", "Disabled by admin — can't save") to Theme.DANGER
        MeCosmeticService.Save.NotOwned -> tr("save_not_owned", "No cosmetic to edit") to Theme.DANGER
        is MeCosmeticService.Save.Invalid -> (outcome.message ?: tr("save_invalid", "Invalid — not saved")) to Theme.DANGER
        is MeCosmeticService.Save.Error -> tr("save_error", "Save failed — try again") to Theme.DANGER
    }

    private fun onEdit() {
        state.saveOutcome = null
    }

    private fun readyDraft(): CosmeticDraft? = state.draft?.takeIf { state.phase == Phase.READY }

    private fun switchTo(target: Tab) {
        if (tab == target) return
        tab = target
        if (target == Tab.WARDROBE) wardrobe.onShow() else wardrobe.onClose()
    }

    override fun onClose() {
        Platform.playerPreview?.clear()
        wardrobe.onClose()
    }


    companion object {
        private const val SCREEN_FRACTION_W = 0.72f
        private const val SCREEN_FRACTION_H = 0.78f
        private const val MIN_WIDTH = 560f
        private const val MIN_HEIGHT = 420f
        private const val EDGE_PAD = 24f

        private const val TOP_CHROME = 32f

        private const val FOOTER_H = 32f

        private const val PREVIEW_FRACTION = 0.32f
        private const val MIN_PREVIEW_W = 220f
        private const val MAX_PREVIEW_W = 340f

        private const val NOTICE_GAP = 12f

        private const val KNOB_BLEED = 6f
        private const val SCROLL_GUTTER = 10f

        private const val NAME_H = 22f
        private const val NAME_BUTTON_MIN_W = 64f

        fun open() {
            UiManager.push(CosmeticEditorScreen())
        }
    }
}
