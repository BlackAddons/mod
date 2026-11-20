package org.blackaddons.core.ui.screen

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.hovered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ConfirmModal
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.*
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.Swatch
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.toHex
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.features.impl.settings.UiTheme
import org.blackaddons.core.theme.ThemeCatalog
import org.blackaddons.core.theme.ThemeDocument
import org.blackaddons.core.theme.ThemePortable
import org.blackaddons.core.ui.screen.portable.PortableExportModal

/** [ThemePortable] reads the import strings under the same prefix. */
private val tr = I18n.scope("blackaddons.theme_screen")

/**
 * Pick or make a theme. Edits apply to [Theme.palette] live, so the whole UI is the preview. Nothing gets saved until
 * Done, and Cancel restores the original. Built-in themes can't be edited, duplicate them first.
 */
internal class ThemeScreen : ModalPanel(tr("title", "Theme")) {

    override val panelWidth: Float get() = fitWidth(740f, margin = 24f)
    override val panelHeight: Float get() = fitHeight(500f, margin = 24f)
    override val closeOnOutsideClick = false

    private var draft: ThemeDocument = UiTheme.current

    private val editable: Boolean get() = ThemeCatalog.byId(draft.id)?.builtIn == false

    /** Unsaved work. Never true for a theme that can't be edited. */
    private val dirty: Boolean
        get() {
            if (!editable) return false
            val stored = ThemeCatalog.byId(draft.id) ?: return true
            val renamed = nameInput.text.trim().let { it.isNotEmpty() && it != stored.name }
            return renamed || stored.spec != draft.spec
        }

    // Resolving a palette is expensive, so it's cached per theme for the list.
    private val resolved = HashMap<ThemeDocument, Palette>()

    private var failures: List<ContrastFailure> = emptyList()

    private val themes = widgets.add(ScrollableList<ThemeDocument>().apply {
        rowHeight = 26f
        rowGap = 2f
        drawRow = { theme, _, x, y, w -> drawThemeRow(theme, x, y, w) }
    })

    private val tokens = widgets.add(ScrollableList<TokenRow>().apply {
        rowHeight = ROW_TOKEN_H
        rowGap = 1f
        drawBackground = false
        drawRow = { row, _, x, y, w -> drawTokenRow(row, x, y, w) }
    })

    private val nameInput = widgets.add(
        TextInput(placeholder = tr("name", "Theme name"), maxLength = NAME_LIMIT).apply {
            text = draft.name
            onEnter = { commit() }
        }
    )

    private val surfaceButton = widgets.add(Button(tr("base_surface", "Surface")) { editBase(ThemeBase.SURFACE) })
    private val textButton = widgets.add(Button(tr("base_text", "Text")) { editBase(ThemeBase.TEXT) })
    private val accentButton = widgets.add(Button(tr("base_accent", "Accent")) { editBase(ThemeBase.ACCENT) })

    private val exportButton = widgets.add(Button(tr("export", "Export")) { export() })
    private val importButton = widgets.add(Button(tr("import", "Import")) { import() })
    private val duplicateButton = widgets.add(Button(tr("duplicate", "Duplicate")) { duplicate() })
    private val deleteButton = widgets.add(Button(tr("delete", "Delete")) { confirmDelete() }.apply { danger = true })
    private val cancelButton = widgets.add(Button(tr("cancel", "Cancel")) { UiManager.pop() })
    private val doneButton = widgets.add(Button(tr("done", "Done")) { commit() }.apply { accent = true })

    init {
        // Cancel is deliberate. Escape is a reflex, so it confirms.
        keys.on(Keys.ESCAPE) { discarding { UiManager.pop() } }
    }

    private sealed interface TokenRow {
        data class Heading(val group: ThemeSlotGroup) : TokenRow
        data class Token(val slot: ThemeSlot) : TokenRow
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val footerH = 24f
        val footerY = contentY + contentH - footerH
        val bodyH = footerY - Theme.PADDING - contentY

        themes.items = ThemeCatalog.all
        themes.render(contentX, contentY, LIST_WIDTH, bodyH)

        val rightX = contentX + LIST_WIDTH + Theme.PADDING_LARGE
        renderEditor(rightX, contentY, contentX + contentW - rightX, bodyH, mouseX, mouseY)
        renderFooter(contentX, footerY, contentW, footerH, mouseX, mouseY)
    }

    private fun renderEditor(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        val baseW = (w - Theme.PADDING_SMALL * 2f) / 3f
        val fixed = HEADER_H + ROW_H * 2f + HEADING_H + Theme.PADDING * 3f + PREVIEW_H
        val tokensH = (h - fixed).coerceAtLeast(MIN_TOKENS_H)

        column(width = w) {
            cell(w, HEADER_H) { cx, cy -> drawHeader(cx, cy, w) }
            spacer(height = Theme.PADDING)
            cell(w, ROW_H) { cx, cy -> drawNameField(cx, cy, w, mouseX, mouseY) }
            spacer(height = Theme.PADDING)
            cell(w, HEADING_H) { cx, cy ->
                heading(tr("bases", "Base colors - everything below is derived from these"), cx, cy)
            }
            row(spacing = Theme.PADDING_SMALL, width = w, height = ROW_H) {
                ThemeBase.entries.forEach { base ->
                    cell(baseW, ROW_H) { cx, cy -> drawBase(base, cx, cy, baseW, mouseX, mouseY) }
                }
            }
            spacer(height = Theme.PADDING)
            cell(w, tokensH) { cx, cy ->
                tokens.items = tokenRows()
                tokens.render(cx, cy, w, tokensH)
            }
            cell(w, PREVIEW_H) { cx, cy -> drawPreview(cx, cy, w) }
        }.draw(x, y)
    }

    private fun drawHeader(x: Float, y: Float, w: Float) {
        Render.text(
            tr("selected", "Selected: %s", draft.name),
            x, y, TextStyle.BODY_PRIMARY_BOLD, maxWidth = w,
        )
        val broken = failures.firstOrNull()
        val note = when {
            broken != null -> tr(
                "warn_contrast",
                "%s cannot be read on %s. Saving is off until it can.",
                slotName(broken.foreground), slotName(broken.background),
            )

            editable -> tr("note_yours", "Your theme. What you change here is kept when you save.")
            else -> tr("note_builtin", "Built in, and read-only. Duplicate it to get one you can change.")
        }
        val color = if (broken != null) Theme.DANGER else Theme.TEXT_MUTED
        Render.text(note, x, y + Theme.FONT_BODY + 4f, Theme.FONT_SMALL, color, Fonts.regular, maxWidth = w)
    }

    private fun drawNameField(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        if (editable) {
            nameInput.render(x, y, w, ROW_H, mouseX, mouseY)
            return
        }
        // Locked themes show the name, not a field that refuses input.
        Render.rect(x, y, w, ROW_H, Theme.SURFACE.withAlpha(120), Theme.RADIUS_SMALL)
        Render.hollowRect(x, y, w, ROW_H, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
        Render.text(
            draft.name,
            x + Theme.PADDING,
            Render.centerY(y, ROW_H, TextStyle.SMALL_MUTED_MEDIUM),
            TextStyle.SMALL_MUTED_MEDIUM,
        )
    }

    private fun drawBase(base: ThemeBase, x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        baseButton(base).apply { enabled = editable }.render(x, y, w, ROW_H, mouseX, mouseY)
        Swatch.draw(x + w - SWATCH_W - 4f, centered(y, ROW_H, SWATCH_H), SWATCH_W, SWATCH_H, draft.spec.bases[base])
        if (hovered(x, y, w, ROW_H)) {
            Tooltip.show(tr("tip_base", "A base color, not a slot. Every color below that says it comes from %s follows this one.", baseName(base)))
        }
    }

    private fun heading(text: String, x: Float, y: Float) = Render.text(
        text.uppercase(), x, y, TextStyle.SMALL_MUTED_BOLD,
    )

    private fun tokenRows(): List<TokenRow> = ThemeSlotGroup.entries.flatMap { group ->
        listOf(TokenRow.Heading(group)) + ThemeSlot.entries.filter { it.group == group }.map(TokenRow::Token)
    }

    // Explicit Unit, because an inferred type would be recursive.
    private fun drawTokenRow(row: TokenRow, x: Float, y: Float, w: Float): Unit = when (row) {
        is TokenRow.Heading -> Render.text(
            I18n.trOr("blackaddons.theme_group.${row.group.id}", row.group.label).uppercase(),
            x,
            Render.centerY(y, ROW_TOKEN_H, TextStyle.SMALL_MUTED_BOLD),
            TextStyle.SMALL_MUTED_BOLD,
        )

        is TokenRow.Token -> drawToken(row.slot, x, y, w)
    }

    private fun drawToken(slot: ThemeSlot, x: Float, y: Float, w: Float) {
        val height = ROW_TOKEN_H
        // Left click pins the color, right click goes back to the formula. Built-ins only show the explanation.
        val hovered = widgets.clickable(x, y, w, height) { if (editable) pinSlot(slot) }
        widgets.clickable(x, y, w, height, Keys.MOUSE_RIGHT) { if (editable) releaseSlot(slot) }
        if (hovered && editable) Render.rect(x, y, w, height, Theme.SURFACE_HOVER.withAlpha(140), Theme.RADIUS_SMALL)

        val color = Theme.palette[slot]
        Swatch.draw(x + Theme.PADDING_SMALL, centered(y, height, SWATCH_H), SWATCH_W, SWATCH_H, color)

        val pinned = slot in draft.spec.overrides
        val hex = color.toHex(withAlpha = color.alpha != 255)
        val tail = if (pinned) "$hex  $PIN" else hex
        val tailW = Render.textWidth(tail, Theme.FONT_SMALL, Fonts.medium)
        val tailX = x + w - Theme.PADDING_SMALL - tailW
        val nameX = x + Theme.PADDING_SMALL + SWATCH_W + Theme.PADDING
        val textY = Render.centerY(y, height, Theme.FONT_SMALL, Fonts.regular)
        val name = I18n.trOr("blackaddons.theme_slot.${slot.id}", slot.label)

        Render.text(
            name, nameX, textY, Theme.FONT_SMALL,
            if (pinned) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
            Fonts.regular, maxWidth = tailX - nameX - Theme.PADDING,
        )
        Render.text(tail, tailX, textY, Theme.FONT_SMALL, if (pinned) Theme.ACCENT else Theme.TEXT_MUTED, Fonts.medium)
        if (hovered) Tooltip.show(tokenTip(slot, pinned))
    }

    private fun tokenTip(slot: ThemeSlot, pinned: Boolean): String {
        val from = slot.derivedFrom
        val origin = when {
            pinned -> tr("tip_pinned", "Set by hand, so no base color moves it.")
            from != null -> tr("tip_derived", "Derived from the %s base color.", baseName(from))
            else -> tr("tip_fixed", "The same in every theme until you set it.")
        }
        if (!editable) return origin
        val action = if (pinned) {
            tr("tip_action_pinned", "Right-click to hand it back to the formula.")
        } else {
            tr("tip_action_derived", "Click to set it by hand.")
        }
        return "$origin $action"
    }

    private fun baseName(base: ThemeBase): String = I18n.trOr("blackaddons.theme_base.${base.id}", base.label)

    /** Theme actions on the left, screen actions on the right, so closing doesn't look like deleting. */
    private fun renderFooter(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        val writable = failures.isEmpty()
        duplicateButton.enabled = writable
        deleteButton.enabled = editable
        doneButton.enabled = writable && (!editable || nameInput.text.isNotBlank())

        val left = listOf(exportButton, importButton, duplicateButton, deleteButton)
        val right = listOf(cancelButton, doneButton)
        val gap = Theme.PADDING_SMALL
        val cells = left.size + right.size
        val buttonW = (w - gap * (cells - 1) - Theme.PADDING_LARGE) / cells

        left.forEachIndexed { index, button ->
            button.render(x + index * (buttonW + gap), y, buttonW, h, mouseX, mouseY)
        }
        val rightX = x + w - right.size * buttonW - (right.size - 1) * gap
        right.forEachIndexed { index, button ->
            button.render(rightX + index * (buttonW + gap), y, buttonW, h, mouseX, mouseY)
        }
    }

    private fun drawThemeRow(theme: ThemeDocument, x: Float, y: Float, w: Float) {
        val height = themes.rowHeight
        val selected = theme.id == draft.id
        val hovered = widgets.clickable(x, y, w, height) { if (!selected) discarding { select(theme) } }
        if (selected || hovered) {
            val fill = if (selected) Theme.ACCENT_SOFT else Theme.SURFACE_HOVER.withAlpha(140)
            Render.rect(x, y, w, height, fill, Theme.RADIUS_SMALL)
        }
        if (selected) Render.rect(x, y + 3f, 2f, height - 6f, Theme.ACCENT, 1f)

        val palette = resolved.getOrPut(theme) { theme.spec.resolve() }
        var swatchX = x + Theme.PADDING
        listOf(ThemeSlot.SURFACE, ThemeSlot.ACCENT, ThemeSlot.TEXT_PRIMARY).forEach { slot ->
            Render.rect(swatchX, centered(y, height, DOT), DOT, DOT, palette[slot], 2f)
            swatchX += DOT + 2f
        }

        val textY = Render.centerY(y, height, Theme.FONT_SMALL, Fonts.medium)
        val mark = if (theme.builtIn) tr("builtin", "built-in") else ""
        val markW = if (mark.isEmpty()) 0f else Render.textWidth(mark, Theme.FONT_SMALL, Fonts.regular)
        if (mark.isNotEmpty()) {
            Render.text(mark, x + w - Theme.PADDING - markW, textY, TextStyle.SMALL_MUTED)
        }

        val textX = swatchX + Theme.PADDING_SMALL
        Render.text(
            theme.name, textX, textY, Theme.FONT_SMALL,
            if (selected) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
            Fonts.medium, maxWidth = x + w - Theme.PADDING - markW - Theme.PADDING_SMALL - textX,
        )
    }

    private fun baseButton(base: ThemeBase): Button = when (base) {
        ThemeBase.SURFACE -> surfaceButton
        ThemeBase.TEXT -> textButton
        ThemeBase.ACCENT -> accentButton
    }

    private fun editBase(base: ThemeBase) {
        if (!editable) return
        UiManager.push(ColorPopover(
            initial = draft.spec.bases[base],
            allowAlpha = false,
            onChange = { edit(draft.spec.copy(bases = draft.spec.bases.with(base, it))) },
        ))
    }

    private fun pinSlot(slot: ThemeSlot) {
        UiManager.push(ColorPopover(
            initial = Theme.palette[slot],
            allowAlpha = true,
            onChange = { edit(draft.spec.copy(overrides = draft.spec.overrides + (slot to it))) },
        ))
    }

    private fun releaseSlot(slot: ThemeSlot) {
        if (slot !in draft.spec.overrides) return
        edit(draft.spec.copy(overrides = draft.spec.overrides - slot))
    }

    private fun edit(spec: ThemeSpec) {
        draft = draft.copy(spec = spec)
        val palette = spec.resolve()
        Theme.palette = palette
        failures = palette.contrastFailures()
    }

    private fun commit() {
        if (dirty) {
            val name = nameInput.text.trim().ifBlank { draft.name }
            val document = ThemeDocument(draft.id, name, draft.spec)
            if (!ThemeCatalog.save(document)) {
                Toasts.show(tr("save_failed", "Could not save the theme"), kind = ToastKind.ERROR)
                return
            }
            select(document)
            Toasts.show(tr("saved", "Saved %s", name), kind = ToastKind.SUCCESS)
        }
        UiTheme.select(draft)
        UiManager.pop()
    }

    private fun duplicate() {
        val name = tr("copy_of", "%s copy", nameInput.text.trim().ifBlank { draft.name })
        val document = ThemeDocument(ThemeCatalog.freeId(name), name, draft.spec)
        if (!ThemeCatalog.save(document)) {
            Toasts.show(tr("save_failed", "Could not save the theme"), kind = ToastKind.ERROR)
            return
        }
        select(document)
        Toasts.show(tr("duplicated", "Duplicated as %s", name), kind = ToastKind.SUCCESS)
    }

    private fun confirmDelete() {
        val target = ThemeCatalog.byId(draft.id)?.takeIf { !it.builtIn } ?: return
        UiManager.push(ConfirmModal(
            title = tr("delete_title", "Delete theme"),
            message = tr("delete_message", "%s will be gone for good.", target.name),
            onConfirm = {
                ThemeCatalog.delete(target.id)
                UiTheme.refresh()
                select(UiTheme.current)
            },
        ))
    }

    private fun export() {
        UiManager.push(PortableExportModal(
            tr("export_title", "Theme exported"),
            tr("export_done", "\"%s\" is on your clipboard.", draft.name),
            ThemeCatalog.export(draft),
        ))
    }

    private fun import() {
        PortableImports.open(ThemePortable, Platform.clipboard.paste()) { imported -> discarding { select(imported) } }
    }

    private fun select(theme: ThemeDocument) {
        draft = theme
        nameInput.text = theme.name
        failures = emptyList() // Already validated by the catalog.
        Theme.palette = theme.spec.resolve()
    }

    private fun slotName(slot: ThemeSlot): String =
        I18n.trOr("blackaddons.theme_slot.${slot.id}", slot.label)

    private fun discarding(action: () -> Unit) {
        if (!dirty) {
            action()
            return
        }
        UiManager.push(ConfirmModal(
            title = tr("unsaved_title", "Unsaved changes"),
            message = tr("unsaved_message", "%s has changes you never saved. Leave them behind?", draft.name),
            confirmLabel = tr("unsaved_discard", "Discard"),
            onConfirm = action,
        ))
    }

    // Nothing gets committed unless Done did it.
    override fun onClose() = ThemeCatalog.apply(UiTheme.current)

    internal companion object {
        const val LIST_WIDTH = 210f
        const val ROW_TOKEN_H = 20f
        const val HEADER_H = 30f
        const val HEADING_H = 14f
        const val ROW_H = 22f
        const val PREVIEW_H = 104f
        const val MIN_TOKENS_H = 80f
        const val SWATCH_W = 18f
        const val SWATCH_H = 14f
        const val CHIP_H = 16f
        const val STATE_H = 16f
        const val DOT = 8f
        const val NAME_LIMIT = 32
        const val PIN = "●"
    }
}
