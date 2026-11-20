package org.blackaddons.core.ui.screen

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ProgressBar
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.api.ui.widget.TabStrip
import org.blackaddons.api.ui.widget.text.AnimatedText
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.api.util.formatTrimmed
import org.blackaddons.core.features.impl.visual.FontSettings
import org.blackaddons.core.font.FontCatalog
import org.blackaddons.core.font.FontCatalogEntry
import org.blackaddons.core.font.FontCatalogState
import org.blackaddons.core.font.FontLoadProgress
import org.blackaddons.core.font.FontsDocument
import org.blackaddons.core.font.GlobalFontDocument
import org.blackaddons.core.font.GlobalFontDraft
import org.blackaddons.core.font.InstalledFont
import kotlin.math.floor

internal class GlobalFontScreen : ModalPanel(I18n.trOr("blackaddons.global_font_screen.title", "Fonts")) {
    internal val tr = I18n.scope("blackaddons.global_font_screen")

    internal var tab = Tab.MINECRAFT
    private val picks = FontSettings.document.let {
        mapOf(
            Tab.MINECRAFT to Pick(it.minecraft.font, it.minecraft.draft),
            Tab.HUD to Pick(it.hud.font, it.hud.draft),
            Tab.WORLD to Pick(it.world.font, it.world.draft),
        )
    }
    private val pick: Pick get() = picks.getValue(tab)

    internal val tuning: Boolean get() = tab == Tab.HUD || selected.isFile
    internal val native: FontSettings.NativeFont?
        get() = when (tab) {
            Tab.MINECRAFT -> FontSettings.minecraft
            Tab.HUD -> null
            Tab.WORLD -> FontSettings.world
        }

    private val tabBar = widgets.add(TabStrip(
        tabs = listOf(
            TabStrip.Tab(Tab.MINECRAFT.name, tr("tab_minecraft", "Minecraft")),
            TabStrip.Tab(Tab.HUD.name, tr("tab_hud", "HUD")),
            TabStrip.Tab(Tab.WORLD.name, tr("tab_world", "World")),
        ),
        activeId = tab.name,
        onSelect = ::selectTab,
    ))

    override val panelWidth: Float get() = fitWidth(680f, margin = 24f)
    override val panelHeight: Float get() = fitHeight(470f, margin = 24f)
    override val closeOnOutsideClick = false

    internal val search = widgets.add(TextInput(placeholder = tr("search", "Search fonts...")))
    internal val list = widgets.add(ScrollableList<FontRow>().apply {
        rowHeight = 22f
        rowGap = 1f
        drawRow = { row, _, x, y, w -> drawFontRow(row, x, y, w) }
    })
    private val importButton = widgets.add(Button(tr("import", "Import")) { chooseLocal() })
    private val applyButton = widgets.add(Button(tr("apply", "Apply")) { apply() }.apply { accent = true })
    private val cancelButton = widgets.add(Button(tr("cancel", "Cancel")) { UiManager.pop() })
    private val resetButton = widgets.add(Button(tr("reset", "Reset")) {
        val defaults = FontsDocument.DEFAULT
        when (tab) {
            Tab.MINECRAFT -> defaults.minecraft
            Tab.HUD -> defaults.hud
            Tab.WORLD -> defaults.world
        }.let {
            selected = it.font
            draft = it.draft
        }
        syncControls()
    })
    private val deleteButton = widgets.add(Button(tr("delete", "Delete")) { confirmDelete() }.apply { danger = true })
    internal val boldButton = widgets.add(Button("") { draft = draft.copy(forceBold = !draft.forceBold) })
    internal val italicButton = widgets.add(Button("") { draft = draft.copy(forceItalic = !draft.forceItalic) })
    internal val shadowButton = widgets.add(Button("") { draft = draft.copy(shadowEnabled = !draft.shadowEnabled) })
    internal val outlineButton = widgets.add(Button("") { draft = draft.copy(outlineEnabled = !draft.outlineEnabled) })
    internal val shadowColorButton = widgets.add(Button(tr("shadow_color", "Shadow color")) { openShadowColor() })
    internal val outlineColorButton = widgets.add(Button(tr("outline_color", "Outline color")) { openOutlineColor() })
    internal val selectedText = AnimatedText(fontSize = Theme.FONT_SMALL, font = Fonts.medium)
    internal val statusText = AnimatedText(fontSize = LABEL_SIZE)
    internal val libraryStatusText = AnimatedText(fontSize = LABEL_SIZE)
    internal val previewReveal = Animation(1f, Theme.ANIM_NORMAL.toLong(), Easing.easeOutBack)
    internal val selectionPulse = Animation(0f, Theme.ANIM_NORMAL.toLong())
    private val rowHover = HashMap<String, Animation>()
    private val rowSelection = HashMap<String, Animation>()

    internal val rasterSlider = slider(GlobalFontDraft.RASTER_SIZE, 0.5) {
        draft = draft.copy(rasterSize = it)
        updateOversamplingLimits()
    }
    internal val oversample2dSlider = slider(GlobalFontDraft.OVERSAMPLE_2D, 1.0) { draft = draft.copy(oversample2d = it) }
    internal val oversampleWorldSlider = slider(GlobalFontDraft.OVERSAMPLE, 1.0) { draft = draft.copy(oversampleWorld = it) }
    internal val spacingSlider = slider(GlobalFontDraft.LETTER_SPACING, 0.25) { draft = draft.copy(letterSpacing = it) }
    internal val shiftXSlider = slider(GlobalFontDraft.SHIFT_X, 0.25) { draft = draft.copy(shiftX = it) }
    internal val shiftYSlider = slider(GlobalFontDraft.SHIFT_Y, 0.25) { draft = draft.copy(shiftY = it) }
    internal val shadowXSlider = slider(GlobalFontDraft.SHADOW_OFFSET, 0.25) { draft = draft.copy(shadowX = it) }
    internal val shadowYSlider = slider(GlobalFontDraft.SHADOW_OFFSET, 0.25) { draft = draft.copy(shadowY = it) }
    internal val outlineSlider = slider(GlobalFontDraft.OUTLINE_WIDTH, 0.05) {
        draft = draft.copy(outlineWidth = it)
    }

    internal var selected: InstalledFont
        get() = pick.font
        set(value) {
            if (pick.font.key == value.key) return
            pick.font = value
            revealPreview()
        }
    internal var draft: GlobalFontDraft
        get() = pick.draft
        set(value) {
            pick.draft = value
        }
    internal var downloading = false
    private var removing = false
    private val busy: Boolean get() = downloading || removing
    internal var operationError: String? = null
    private var previewKey: String? = null
    private var cachedPreviewFont: Font = Fonts.regular
    private var lastSearch = ""
    private var activityVisibleUntil = 0L
    private var transferKey: String? = null

    @Volatile private var pendingInstall: Result<InstalledFont>? = null
    @Volatile private var pendingRemoval: Result<InstalledFont>? = null
    @Volatile internal var transferProgress: FontLoadProgress? = null

    init {
        picks.values.forEach { FontCatalog.track(it.font) }
        FontCatalog.load()
        val defaults = GlobalFontDraft.DEFAULT
        rasterSlider.defaultValue = defaults.rasterSize
        oversample2dSlider.defaultValue = defaults.oversample2d
        oversampleWorldSlider.defaultValue = defaults.oversampleWorld
        spacingSlider.defaultValue = defaults.letterSpacing
        shiftXSlider.defaultValue = defaults.shiftX
        shiftYSlider.defaultValue = defaults.shiftY
        shadowXSlider.defaultValue = defaults.shadowX
        shadowYSlider.defaultValue = defaults.shadowY
        outlineSlider.defaultValue = defaults.outlineWidth
        syncControls()
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        tabBar.render(panelX, panelY, mouseX, mouseY)
        pendingInstall?.let {
            pendingInstall = null
            downloading = false
            transferKey = null
            it.onSuccess { font ->
                selected = font
                operationError = null
                transferProgress = FontLoadProgress(tr("ready", "%s is ready", font.displayName), 1.0)
                activityVisibleUntil = System.currentTimeMillis() + READY_VISIBLE_MS
            }.onFailure { failure ->
                operationError = failure.message ?: tr("load_failed", "Font could not be loaded")
                transferProgress = null
            }
        }
        pendingRemoval?.let {
            pendingRemoval = null
            removing = false
            it.onSuccess { font ->
                picks.values.filter { pick -> pick.font.key == font.key }.forEach { pick -> pick.font = InstalledFont.BUNDLED }
                revealPreview()
                operationError = null
                transferProgress = FontLoadProgress(tr("removed", "%s removed", font.displayName), 1.0)
                activityVisibleUntil = System.currentTimeMillis() + READY_VISIBLE_MS
            }.onFailure { failure ->
                operationError = failure.message ?: tr("remove_failed", "Font could not be removed")
                transferProgress = null
            }
        }
        if (!busy && activityVisibleUntil != 0L && System.currentTimeMillis() >= activityVisibleUntil) {
            transferProgress = null
            activityVisibleUntil = 0L
        }

        val leftW = minOf(220f, contentW * 0.35f)
        val importW = 66f
        importButton.enabled = !busy
        if (search.text != lastSearch) {
            lastSearch = search.text
            list.resetScroll()
        }
        list.items = matchingFonts()
        list.emptyMessage = when (val state = FontCatalog.state) {
            FontCatalogState.Loading -> tr("loading_fontsource", "Loading Fontsource...")
            is FontCatalogState.Failed -> state.message
            is FontCatalogState.Ready -> tr("no_matching_fonts", "No matching fonts.")
        }
        val rightW = contentW - leftW - 18f
        val applying = FontSettings.minecraft.applying || FontSettings.world.applying
        applyButton.label = if (applying) tr("applying", "Applying...") else tr("apply", "Apply")
        applyButton.enabled = !busy && !applying
        deleteButton.label = if (removing) tr("deleting", "Deleting...") else tr("delete", "Delete")
        deleteButton.enabled = selected.isFile && !selected.isBundled && !busy && !applying

        val left = column(width = leftW, height = contentH) {
            row(spacing = 6f, width = leftW, height = 22f) {
                cell(leftW - importW - 6f, 22f) { x, y -> search.render(x, y, leftW - importW - 6f, 22f, mouseX, mouseY) }
                cell(importW, 22f) { x, y -> importButton.render(x, y, importW, 22f, mouseX, mouseY) }
            }
            spacer(height = 7f)
            cell(leftW, 18f) { x, y -> drawLibraryStatus(x, y, leftW, list.items) }
            cell(leftW, contentH - 47f) { x, y -> list.render(x, y, leftW, contentH - 47f) }
        }
        val controlsH = contentH - 32f
        val right = column(width = rightW, height = contentH) {
            cell(rightW, controlsH) { x, y -> drawControls(x, y, rightW, controlsH, mouseX, mouseY) }
            flexSpacer()
            row(spacing = 6f, align = Align.CENTER, width = rightW, height = 24f) {
                cell(70f, 24f) { x, y -> resetButton.render(x, y, 70f, 24f, mouseX, mouseY) }
                cell(76f, 24f) { x, y -> deleteButton.render(x, y, 76f, 24f, mouseX, mouseY) }
                flexSpacer()
                cell(76f, 24f) { x, y -> cancelButton.render(x, y, 76f, 24f, mouseX, mouseY) }
                cell(76f, 24f) { x, y -> applyButton.render(x, y, 76f, 24f, mouseX, mouseY) }
            }
        }
        row(spacing = 18f, width = contentW, height = contentH) {
            add(left)
            add(right)
        }.draw(contentX, contentY)
    }

    internal fun LayoutBuilder.sliderRow(
        leftLabel: String, leftValue: Double, leftSlider: Slider,
        rightLabel: String, rightValue: Double, rightSlider: Slider,
        width: Float, mouseX: Float, mouseY: Float, rightSuffix: String = "",
        leftShown: String = format(leftValue),
    ) {
        val gap = 12f
        val cellW = (width - gap) / 2f
        row(spacing = gap, width = width, height = CONTROL_ROW_H) {
            cell(cellW, CONTROL_ROW_H) { cx, cy ->
                drawCompactSlider(leftLabel, leftValue, leftSlider, cx, cy, cellW, mouseX, mouseY, shown = leftShown)
            }
            cell(cellW, CONTROL_ROW_H) { cx, cy ->
                drawCompactSlider(rightLabel, rightValue, rightSlider, cx, cy, cellW, mouseX, mouseY, rightSuffix)
            }
        }
    }

    internal fun LayoutBuilder.singleSliderRow(
        label: String,
        value: Double,
        slider: Slider,
        width: Float,
        mouseX: Float,
        mouseY: Float,
    ) {
        val cellW = (width - 12f) / 2f
        cell(width, CONTROL_ROW_H) { cx, cy ->
            drawCompactSlider(label, value, slider, cx, cy, cellW, mouseX, mouseY)
        }
    }

    internal val look: GlobalFontDraft get() = if (tuning && tab != Tab.HUD) draft else GlobalFontDraft.WORLD_DEFAULT

    internal fun previewFont(): Font {
        val key = "$tab:${selected.key}:${draft.hashCode()}"
        if (previewKey == key) return cachedPreviewFont
        previewKey = key
        val font = if (tab == Tab.HUD) selected.hudFont(draft) else selected.uiFont(look.forceBold)
        return font.also { cachedPreviewFont = it }
    }

    private fun drawFontRow(row: FontRow, x: Float, y: Float, w: Float) {
        val hovered = widgets.clickable(x, y, w, list.rowHeight) { select(row) }
        val isSelected = row.key == selected.key
        val isDownloading = downloading && row.key == transferKey
        val emphasized = isSelected || isDownloading
        val hover = rowHover.getOrPut(row.key) { Animation(durationMs = Theme.ANIM_HOVER.toLong()) }
        val selection = rowSelection.getOrPut(row.key) { Animation(if (emphasized) 1f else 0f, Theme.ANIM_FAST.toLong()) }
        hover.animateTo(if (hovered) 1f else 0f)
        selection.animateTo(if (emphasized) 1f else 0f)
        val base = if (row.installed != null) Theme.ACCENT.withAlpha(24) else Theme.SURFACE.withAlpha(0)
        val hoveredColor = lerpColor(base, Theme.SURFACE_HOVER, hover.get())
        val selectedAmount = selection.get()
        val background = lerpColor(hoveredColor, Theme.ACCENT.withAlpha(62), selectedAmount)
        if (background.alpha > 0) Render.rect(x + 2f, y, w - 4f, list.rowHeight, background, Theme.RADIUS_SMALL)
        if (selectedAmount > 0f) {
            val markerH = (list.rowHeight - 8f) * selectedAmount
            Render.rect(x + 3f, centered(y, list.rowHeight, markerH), 2f, markerH, Theme.ACCENT, 1f)
        }
        val tag = when {
            isDownloading -> tr("tag_loading", "loading")
            isSelected -> tr("tag_selected", "selected")
            row.installed?.isFile == false -> ""
            row.installed != null -> tr("tag_installed", "installed")
            else -> row.catalog?.category.orEmpty()
        }
        val tagWidth = Render.textWidth(tag, LABEL_SIZE, Fonts.regular)
        Render.text(row.name, x + 8f, Render.centerY(y, list.rowHeight, TextStyle.SMALL_PRIMARY), TextStyle.SMALL_PRIMARY, maxWidth = w - tagWidth - 20f)
        Render.text(tag, x + w - tagWidth - 8f, Render.centerY(y, list.rowHeight, LABEL_SIZE, Fonts.regular), LABEL_SIZE,
            if (row.installed != null || emphasized) Theme.ACCENT else Theme.TEXT_MUTED, Fonts.regular)
        if (isDownloading) ProgressBar.drawIndeterminate(x + 7f, y + list.rowHeight - 3f, w - 14f, PROGRESS_H)
    }

    private fun chooseLocal() {
        Platform.desktop.chooseFile(tr("choose_font", "Import font"), FONT_EXTENSIONS) { path ->
            beginTransfer(tr("preparing_local", "Preparing local font..."))
            FontCatalog.import(path, onProgress = { transferProgress = it }) { pendingInstall = it }
        }
    }

    private fun select(row: FontRow) {
        if (busy) return
        row.installed?.let {
            selected = it
            operationError = null
            return
        }
        val entry = row.catalog ?: return
        beginTransfer(tr("preparing_font", "Preparing %s...", entry.family), row.key)
        FontCatalog.install(entry, onProgress = { transferProgress = it }) { pendingInstall = it }
    }

    private fun beginTransfer(label: String, key: String? = null) {
        downloading = true
        transferKey = key
        operationError = null
        activityVisibleUntil = 0L
        transferProgress = FontLoadProgress(label, null)
    }

    private fun confirmDelete() {
        if (!selected.isFile || selected.isBundled || busy) return
        val font = selected
        UiManager.confirmPopover(
            message = tr("delete_confirm", "Delete %s from installed fonts?", font.displayName),
        ) {
            removing = true
            operationError = null
            activityVisibleUntil = 0L
            transferProgress = FontLoadProgress(tr("deleting_font", "Deleting %s...", font.displayName), null)
            FontCatalog.uninstall(font) { result ->
                Platform.scheduler.schedule(0) {
                    if (result.isSuccess) FontSettings.uninstalled(font)
                    pendingRemoval = result
                }
            }
        }
    }

    private fun apply() {
        val (minecraft, hud, world) = listOf(Tab.MINECRAFT, Tab.HUD, Tab.WORLD).map { picks.getValue(it).document }
        FontSettings.apply(FontsDocument(minecraft, hud, world))
    }

    private fun selectTab(id: String) {
        tab = Tab.valueOf(id)
        operationError = null
        revealPreview()
        syncControls()
    }

    private fun revealPreview() {
        previewKey = null
        previewReveal.set(0f)
        previewReveal.animateTo(1f)
        selectionPulse.set(1f)
        selectionPulse.animateTo(0f)
    }

    private fun openShadowColor() {
        UiManager.push(ColorPopover(
            initial = draft.shadowColor,
            allowAlpha = true,
            default = GlobalFontDraft.DEFAULT.shadowColor,
            onChange = { draft = draft.copy(shadowColor = it) },
        ))
    }

    private fun openOutlineColor() {
        UiManager.push(ColorPopover(
            initial = draft.outlineColor,
            allowAlpha = true,
            default = GlobalFontDraft.DEFAULT.outlineColor,
            onChange = { draft = draft.copy(outlineColor = it) },
        ))
    }

    private fun updateOversamplingLimits() {
        val max = floor(GlobalFontDraft.MAX_RASTERIZED_EM / draft.rasterSize).coerceIn(GlobalFontDraft.OVERSAMPLE)
        oversample2dSlider.max = max
        oversampleWorldSlider.max = max
        if (draft.oversample2d > max) draft = draft.copy(oversample2d = max)
        if (draft.oversampleWorld > max) draft = draft.copy(oversampleWorld = max)
        oversample2dSlider.value = draft.oversample2d
        oversampleWorldSlider.value = draft.oversampleWorld
    }

    private fun syncControls() {
        rasterSlider.value = draft.rasterSize
        updateOversamplingLimits()
        spacingSlider.value = draft.letterSpacing
        shiftXSlider.value = draft.shiftX
        shiftYSlider.value = draft.shiftY
        shadowXSlider.value = draft.shadowX
        shadowYSlider.value = draft.shadowY
        outlineSlider.value = draft.outlineWidth
    }

    internal fun slider(range: ClosedFloatingPointRange<Double>, step: Double, changed: (Double) -> Unit) =
        widgets.add(Slider(range.start, range.endInclusive, range.start, step, changed).apply { defaultValue = range.start })

    internal fun toggleLabel(name: String, enabled: Boolean) =
        "$name: ${if (enabled) tr("on", "On") else tr("off", "Off")}"

    internal fun format(value: Double) = value.formatTrimmed()

    internal data class FontRow(
        val installed: InstalledFont? = null,
        val catalog: FontCatalogEntry? = null,
    ) {
        val key: String get() = installed?.key ?: "${FontCatalog.FONTSOURCE_KEY_PREFIX}${requireNotNull(catalog).id}"
        val name: String get() = installed?.label ?: requireNotNull(catalog).family
    }

    internal enum class Tab { MINECRAFT, HUD, WORLD }

    private class Pick(var font: InstalledFont, var draft: GlobalFontDraft) {
        val document: GlobalFontDocument get() = GlobalFontDocument(font, draft)
    }

    internal companion object {
        const val LABEL_SIZE = 9f
        const val CONTROL_ROW_H = 31f
        const val PROGRESS_H = 3f
        const val NOTE_H = 16f
        const val READY_VISIBLE_MS = 900L
        val FONT_EXTENSIONS = setOf("ttf", "otf")
        val PREVIEW_LINES = listOf(
            "The quick brown fox jumps over the lazy dog.",
            "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG.",
            "0123456789  !@#\$%^&*()  [] {}",
            "Minecraft UI · ❤ ✎ ❈",
            "I love femboys ❤",
        )
        val OUTLINE = arrayOf(
            -1f to -1f, 0f to -1f, 1f to -1f,
            -1f to 0f, 1f to 0f,
            -1f to 1f, 0f to 1f, 1f to 1f,
        )
    }
}
