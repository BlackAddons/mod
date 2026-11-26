package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.TabStrip
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.world.BlockCatalogEntry
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.features.impl.visual.fme.DEFAULT_TINT_RGB
import org.blackaddons.core.features.impl.visual.fme.FME_ANY_SOURCE
import org.blackaddons.core.features.impl.visual.fme.Fme
import org.blackaddons.core.features.impl.visual.fme.FmeManagedGroup
import org.blackaddons.core.features.impl.visual.fme.FmeManagedKind
import org.blackaddons.core.features.impl.visual.fme.FmePortable
import org.blackaddons.core.features.impl.visual.fme.FmeScope
import org.blackaddons.core.ui.screen.fme.FmeBlockBrowser.Companion.displayName
import org.blackaddons.core.ui.screen.portable.PortableExportModal
import org.blackaddons.core.ui.screen.profile.ProfileMenu
import java.awt.Color

internal class FmeScreen : ModalPanel("") {

    override val closeOnOutsideClick: Boolean get() = false
    override val panelWidth: Float
        get() = widthShare(0.85f, min = 620f)
    override val panelHeight: Float
        get() = heightShare(0.8f, min = 380f)

    private val profileButton = widgets.add(Button("") { openProfileMenu() })
    private val editButton = widgets.add(Button(tr("edit_mode_button", "Edit Mode")) { enterEditMode() }.apply { accent = true })
    private val applyButton = widgets.add(Button(tr("apply_rule", "Apply")) { applyRule() }.apply { accent = true })
    private val cancelRuleButton = widgets.add(Button("×") { cancelRule() })
    private val search = widgets.add(TextInput(placeholder = tr("search", "Search blocks...")))
    private val contextDropdown = widgets.add(Dropdown())
    private val replaceDropdown = widgets.add(Dropdown().apply {
        items = listOf(Dropdown.Item(tr("same_block_type", "Same block type")), Dropdown.Item(tr("any_block", "Any block")))
        onSelect = { index -> if ((index == 1) != Fme.isAnyBlockMode()) Fme.toggleAnyBlockMode() }
    })
    private val ruleAnimation = Animation(durationMs = Theme.ANIM_NORMAL.toLong())

    private var activeScope = lastActiveScope
    private val selectedContexts = mutableMapOf<FmeScope, String>()
    private val pinnedContexts = mutableSetOf<FmeScope>()
    private var shownContextKeys = emptyList<String>()
    private val contextRow = Rect()
    private var replaceSourceId: String? = null
    private var replaceGroup: FmeManagedGroup? = null
    private val browser = FmeBlockBrowser(
        widgets = widgets,
        search = search,
        scope = { activeScope },
        contextKey = ::contextKey,
        selectBlock = ::select,
        openBlockMenu = ::openBlockMenu,
        openChangedMenu = ::openChangedMenu,
    )

    private val tabBar = widgets.add(TabStrip(
        tabs = listOf(
            TabStrip.Tab(FmeScope.GLOBAL.name, tr("tab_global", "Global")),
            TabStrip.Tab(FmeScope.LOCATION.name, tr("tab_locations", "Locations")),
            TabStrip.Tab(FmeScope.DUNGEON.name, tr("tab_rooms", "Dungeon Rooms")),
        ),
        activeId = activeScope.name,
        onSelect = ::selectTab,
    ))

    init {
        search.onTextChanged = { browser.reset() }
        contextDropdown.onSelect = { index ->
            shownContextKeys.getOrNull(index)?.let { key ->
                selectedContexts[activeScope] = key
                val current = Fme.currentScopeKey(activeScope)
                if (key == current) pinnedContexts -= activeScope else pinnedContexts += activeScope
                browser.reset()
                cancelRule()
            }
        }
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        tabBar.render(panelX, panelY, mouseX, mouseY)
        var y = contentY
        renderHeader(y, mouseX, mouseY)
        y += HEADER_H + Theme.PADDING_SMALL

        renderSearch(y, mouseX, mouseY)
        y += SEARCH_H + Theme.PADDING_SMALL

        contextRow.clear()
        if (activeScope != FmeScope.GLOBAL) {
            renderContext(y, mouseX, mouseY)
            y += CONTEXT_H + Theme.PADDING_SMALL
        }

        val ruleProgress = ruleAnimation.also { it.animateTo(if (replaceSourceId == null) 0f else 1f) }.get()
        if (ruleProgress > 0f) {
            renderRuleBar(y, ruleProgress, mouseX, mouseY)
            y += RULE_BAR_H * ruleProgress + Theme.PADDING_SMALL
        } else {
            applyButton.clear()
            cancelRuleButton.clear()
        }

        val browserH = (contentY + contentH - y).coerceAtLeast(0f)
        browser.render(contentX, y, contentW, browserH)
        contextDropdown.renderOverlay(mouseX, mouseY)
        replaceDropdown.renderOverlay(mouseX, mouseY)
    }

    private fun renderHeader(y: Float, mouseX: Float, mouseY: Float) {
        profileButton.label = Fme.activeProfile().name
        val profileW = profileButton.naturalWidth().coerceIn(PROFILE_MIN_W, PROFILE_MAX_W)
        profileButton.render(contentX + contentW - profileW, y, profileW, HEADER_H, mouseX, mouseY)
        editButton.enabled = Fme.enabled
        editButton.render(contentX + contentW - profileW - Theme.PADDING_SMALL - EDIT_W, y, EDIT_W, HEADER_H, mouseX, mouseY)
    }

    private fun renderSearch(y: Float, mouseX: Float, mouseY: Float) {
        val selectedW = minOf(SELECTED_W, contentW * 0.34f)
        val searchW = contentW - selectedW - Theme.PADDING_SMALL
        search.render(contentX, y, searchW, SEARCH_H, mouseX, mouseY)

        val x = contentX + searchW + Theme.PADDING_SMALL
        Render.rect(x, y, selectedW, SEARCH_H, Theme.SURFACE.withAlpha(120), Theme.RADIUS_SMALL)
        browser.entry(Fme.selectedBlockId())?.let { Render.item(it.itemId, x + 4f, y + 2f, 20f, 20f, Theme.RADIUS_SMALL) }
        val selected = tr("selected", "Selected: %s", displayName(Fme.selectedBlockId()))
        Render.text(
            selected,
            x + 28f, Render.centerY(y, SEARCH_H, TextStyle.SMALL_SECONDARY_MEDIUM),
            TextStyle.SMALL_SECONDARY_MEDIUM, maxWidth = selectedW - 34f,
        )
    }

    private fun renderContext(y: Float, mouseX: Float, mouseY: Float) {
        syncContext()
        contextRow.set(contentX, y, contentW, CONTEXT_H)
        Render.rect(contentX, y, contentW, CONTEXT_H, Theme.SURFACE.withAlpha(70), Theme.RADIUS_SMALL)
        val label = if (activeScope == FmeScope.LOCATION) tr("location", "Location:") else tr("room", "Room:")
        Render.text(label, contentX + 8f, Render.centerY(y, CONTEXT_H, TextStyle.SMALL_MUTED_MEDIUM), TextStyle.SMALL_MUTED_MEDIUM)
        val labelW = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
        val replaceW = renderReplaceMode(y, mouseX, mouseY)
        if (shownContextKeys.isEmpty()) {
            val unavailable = if (activeScope == FmeScope.LOCATION) tr("location_unavailable", "Location unavailable") else tr("room_unavailable", "Stand inside a scanned dungeon room")
            Render.text(unavailable, contentX + 14f + labelW, Render.centerY(y, CONTEXT_H, Theme.FONT_SMALL, Fonts.regular), Theme.FONT_SMALL, Theme.DANGER, Fonts.regular)
            return
        }
        contextDropdown.render(contentX + labelW + 14f, y + 2f, minOf(CONTEXT_W, contentW - labelW - 22f - replaceW), CONTEXT_H - 4f, mouseX, mouseY)
    }

    private fun renderReplaceMode(y: Float, mouseX: Float, mouseY: Float): Float {
        val label = tr("edit_mode_replaces", "Edit Mode replaces:")
        val labelW = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
        val dropdownX = contentX + contentW - 2f - REPLACE_W
        val labelX = dropdownX - 6f - labelW
        Render.text(label, labelX, Render.centerY(y, CONTEXT_H, TextStyle.SMALL_MUTED_MEDIUM), TextStyle.SMALL_MUTED_MEDIUM)
        replaceDropdown.selectedIndex = if (Fme.isAnyBlockMode()) 1 else 0
        replaceDropdown.render(dropdownX, y + 2f, REPLACE_W, CONTEXT_H - 4f, mouseX, mouseY)
        return contentX + contentW - labelX + Theme.PADDING_SMALL
    }

    private fun syncContext() {
        val scope = activeScope
        val current = Fme.currentScopeKey(scope)
        val keys = Fme.scopeKeys(scope)
        if (scope !in pinnedContexts || selectedContexts[scope] !in keys) {
            selectedContexts[scope] = current ?: keys.firstOrNull().orEmpty()
            pinnedContexts -= scope
        }
        shownContextKeys = keys
        contextDropdown.items = keys.map { key ->
            Dropdown.Item(if (key == current) tr("current_context", "%s (Current)", key) else key)
        }
        contextDropdown.selectedIndex = keys.indexOf(selectedContexts[scope]).coerceAtLeast(0)
    }

    private fun renderRuleBar(y: Float, progress: Float, mouseX: Float, mouseY: Float) {
        val visibleH = RULE_BAR_H * progress
        Render.pushScissor(contentX, y, contentW, visibleH)
        Render.rect(contentX, y, contentW, RULE_BAR_H, Theme.ACCENT.withAlpha(28), Theme.RADIUS_SMALL)
        val source = replaceSourceId
        if (source != null) {
            val target = Fme.selectedBlockId()
            val text = "${displayName(source)} → ${displayName(target)}"
            val cancelX = contentX + contentW - Theme.PADDING_SMALL - RULE_CANCEL_W
            val applyX = cancelX - Theme.PADDING_SMALL - RULE_APPLY_W
            drawRuleItems(source, target, contentX + 8f, y)
            Render.text(
                text,
                contentX + 72f, Render.centerY(y, RULE_BAR_H, TextStyle.SMALL_PRIMARY_MEDIUM),
                TextStyle.SMALL_PRIMARY_MEDIUM, maxWidth = applyX - contentX - 80f,
            )
            applyButton.enabled = contextKey(activeScope) != null
            applyButton.render(applyX, y + 3f, RULE_APPLY_W, RULE_BAR_H - 6f, mouseX, mouseY)
            cancelRuleButton.render(cancelX, y + 3f, RULE_CANCEL_W, RULE_BAR_H - 6f, mouseX, mouseY)
        }
        Render.popScissor()
    }

    private fun drawRuleItems(source: String, target: String, x: Float, y: Float) {
        browser.entry(source)?.let { Render.item(it.itemId, x, y + 4f, 22f, 22f, Theme.RADIUS_SMALL) }
        Render.text("→", x + 27f, y + 9f, TextStyle.SMALL_MUTED_MEDIUM)
        browser.entry(target)?.let { Render.item(it.itemId, x + 42f, y + 4f, 22f, 22f, Theme.RADIUS_SMALL) }
    }

    private fun openProfileMenu() = ProfileMenu.open(
        FmeProfiles, ::resetForDataChange,
        extra = listOf(ContextMenu.Item(PortableImports.tr("from_file", "Import from file..."), LucideIcons.configs) {
            FmeFileImport.choose(::resetForDataChange)
        }),
    )

    private fun pasteItem() = ContextMenu.Item(PortableImports.tr("paste", "Paste from clipboard"), LucideIcons.clipboardPaste) {
        PortableImports.open(FmePortable, Platform.clipboard.paste()) { resetForDataChange() }
    }

    private fun openWorkspaceMenu() {
        UiManager.contextMenu(listOf(
            ContextMenu.Item(tr("undo", "Undo"), LucideIcons.rotateCcw, enabled = Fme.canUndo()) { Fme.changeHistory(undo = true) },
            ContextMenu.Item(tr("redo", "Redo"), LucideIcons.rotateCw, enabled = Fme.canRedo()) { Fme.changeHistory(undo = false) },
        ))
    }

    private fun openBlockMenu(entry: BlockCatalogEntry, anchor: Rect) {
        if (entry.blockId == FME_ANY_SOURCE) return openBlockTint(entry.blockId, anchor)
        val source = replaceSourceId
        val favorite = entry.blockId in Fme.favoriteBlockIds()
        val entries = buildList<ContextMenu.Entry> {
            add(ContextMenu.Item(tr("select_replacement", "Select as replacement"), LucideIcons.circleCheck) { select(entry) })
            add(ContextMenu.Item(tr("set_source", "Set as source"), LucideIcons.actions) { startRule(entry.blockId) })
            if (source != null && source != entry.blockId) {
                add(ContextMenu.Item(tr("use_destination", "Use as destination"), LucideIcons.circleCheck) { select(entry) })
            }
            if (entry.blockId != Fme.selectedBlockId()) {
                add(ContextMenu.Item(tr("replace_with_selected", "Replace with selected block"), LucideIcons.actions) {
                    startRule(entry.blockId, preloadExisting = false)
                })
            }
            add(ContextMenu.Item(tr("change_tint", "Change tint..."), LucideIcons.settings) { openBlockTint(entry.blockId, anchor) })
            add(ContextMenu.Separator)
            add(ContextMenu.Item(
                if (favorite) tr("unfavorite", "Remove from favorites") else tr("favorite", "Add to favorites"),
                if (favorite) LucideIcons.starFilled else LucideIcons.star,
            ) {
                Fme.toggleFavorite(entry.blockId)
                browser.invalidateBlocks()
            })
            add(ContextMenu.Item(tr("copy_block_id", "Copy block ID"), LucideIcons.copy) { Platform.clipboard.copy(entry.blockId) })
        }
        UiManager.contextMenu(entries)
    }

    private fun openBlockTint(blockId: String, anchor: Rect) {
        val scope = activeScope
        val key = contextKey(scope) ?: return
        var group = Fme.managedGroups(scope, key)
            .firstOrNull { it.kind == FmeManagedKind.TYPE && it.sourceBlockId == blockId }
        var firstChange = true
        UiManager.push(ColorPopover(
            initial = Color(group?.tintRgb ?: DEFAULT_TINT_RGB),
            default = Color(DEFAULT_TINT_RGB),
            anchorX = anchor.x, anchorY = anchor.y, anchorW = anchor.w, anchorH = anchor.h,
        ) { color ->
            if (group == null) {
                if (!Fme.setTypeReplacement(scope, key, blockId, blockId)) return@ColorPopover
                group = Fme.managedGroups(scope, key)
                    .firstOrNull { it.kind == FmeManagedKind.TYPE && it.sourceBlockId == blockId }
                firstChange = false
            }
            if (group?.let { Fme.setTint(it, color.rgb and DEFAULT_TINT_RGB, firstChange) } == true) firstChange = false
        })
    }

    private fun openChangedMenu(group: FmeManagedGroup, anchor: Rect) {
        val entries = buildList<ContextMenu.Entry> {
            val anyTint = group.blockId == FME_ANY_SOURCE
            if (!anyTint) add(ContextMenu.Item(tr("select_replacement", "Select replacement"), LucideIcons.circleCheck) { select(group.blockId) })
            val typedSource = group.sourceBlockId != FME_ANY_SOURCE
            if (typedSource || group.kind == FmeManagedKind.POSITION) {
                add(ContextMenu.Item(tr("change_replacement", "Change replacement..."), LucideIcons.settings) {
                    select(group.blockId)
                    startRule(group.sourceBlockId, preloadExisting = false)
                    if (group.kind == FmeManagedKind.POSITION) replaceGroup = group
                })
            }
            if (typedSource) {
                add(ContextMenu.Item(tr("set_source", "Set as source"), LucideIcons.actions) { startRule(group.sourceBlockId) })
            }
            if (typedSource && group.kind == FmeManagedKind.POSITION) {
                add(ContextMenu.Item(tr("replace_all_like_this", "Replace all blocks like this"), LucideIcons.actions) {
                    select(group.blockId)
                    startRule(group.sourceBlockId, preloadExisting = false)
                })
                if (!group.ghost) {
                    add(ContextMenu.Item(tr("match_any_block", "Match any block"), LucideIcons.actions) { Fme.matchAnyBlock(group) })
                }
            }
            add(ContextMenu.Separator)
            add(ContextMenu.Item(tr("set_tint", "Set tint..."), LucideIcons.settings) { openTint(group, anchor) })
            if (group.tintRgb != DEFAULT_TINT_RGB) {
                add(ContextMenu.Item(tr("reset_tint", "Reset tint"), LucideIcons.rotateCcw) { Fme.setTint(group, DEFAULT_TINT_RGB) })
            }
            add(ContextMenu.Item(if (group.enabled) tr("disable_rule", "Disable") else tr("enable_rule", "Enable"), if (group.enabled) LucideIcons.eyeOff else LucideIcons.eye) {
                Fme.setGroupEnabled(group, !group.enabled)
            })
            if (group.kind == FmeManagedKind.POSITION) {
                add(ContextMenu.Item(tr("inspect_positions", "Inspect positions..."), LucideIcons.maximize) { UiManager.push(FmePositionModal(group)) })
            } else {
                add(ContextMenu.Item(tr("move_copy", "Move / Copy..."), LucideIcons.copy) { UiManager.push(FmeRuleTransferModal(group)) })
            }
            if (!anyTint) add(ContextMenu.Item(tr("copy_block_id", "Copy replacement ID"), LucideIcons.copy) { Platform.clipboard.copy(group.blockId) })
            add(ContextMenu.Item(PortableImports.tr("share", "Share"), LucideIcons.share) {
                FmePortable.export(group)?.let { PortableExportModal.share(group.blockId.removePrefix("minecraft:"), it) }
            })
            add(pasteItem())
            add(ContextMenu.Separator)
            add(ContextMenu.Item(
                tr("remove_rule", "Remove"), LucideIcons.circleX, danger = true,
                confirm = tr("remove_confirm", "Remove this FME edit?"),
            ) { Fme.removeGroup(group) })
        }
        UiManager.contextMenu(entries)
    }

    private fun openTint(group: FmeManagedGroup, anchor: Rect) {
        var firstChange = true
        UiManager.push(ColorPopover(
            initial = Color(group.tintRgb),
            default = Color(DEFAULT_TINT_RGB),
            anchorX = anchor.x, anchorY = anchor.y, anchorW = anchor.w, anchorH = anchor.h,
        ) { color ->
            if (Fme.setTint(group, color.rgb and DEFAULT_TINT_RGB, firstChange)) firstChange = false
        })
    }

    private fun openContextMenu(scope: FmeScope) {
        val key = contextKey(scope) ?: return
        val count = Fme.managedGroups(scope, key).sumOf(FmeManagedGroup::count)
        if (scope == FmeScope.GLOBAL && count == 0) return
        val copied = copiedContext?.takeIf { it.profileId == Fme.activeProfile().id && it.scope == scope && it.key != key }
        val entries = buildList<ContextMenu.Entry> {
            if (scope != FmeScope.GLOBAL) {
                add(ContextMenu.Item(tr("copy_context", "Copy all rules"), LucideIcons.copy, enabled = count > 0) {
                    copiedContext = CopiedContext(Fme.activeProfile().id, scope, key)
                    Toasts.show(tr("context_copied", "Copied %d FME edits from %s.", count, key), kind = ToastKind.SUCCESS)
                })
                add(ContextMenu.Item(
                    copied?.let { tr("paste_context", "Paste rules from %s", it.key) } ?: tr("paste_context_empty", "Paste rules"),
                    LucideIcons.clipboardPaste, enabled = copied != null,
                ) {
                    val pasted = copied?.let { Fme.copyContext(scope, it.key, key) } ?: 0
                    if (pasted > 0) Toasts.show(tr("context_pasted", "Pasted %d FME edits into %s.", pasted, key), kind = ToastKind.SUCCESS)
                })
                add(ContextMenu.Separator)
            }
            val label = if (scope == FmeScope.GLOBAL) tr("clear_global", "Clear Global") else tr("clear_context", "Clear %s", key)
            add(ContextMenu.Item(
                label, LucideIcons.circleX, danger = true, enabled = count > 0,
                confirm = tr("clear_confirm", "Remove %d edits from %s? This can be undone.", count, key.ifEmpty { tr("everywhere", "Everywhere") }),
            ) {
                val removed = Fme.clearContext(scope, key)
                if (removed > 0) Toasts.show(tr("cleared_count", "Cleared %d FME edits.", removed), kind = ToastKind.SUCCESS)
            })
        }
        UiManager.contextMenu(entries)
    }

    private fun startRule(sourceId: String, preloadExisting: Boolean = true) {
        replaceSourceId = sourceId
        replaceGroup = null
        if (preloadExisting) {
            Fme.managedGroups(activeScope, contextKey(activeScope).orEmpty())
                .firstOrNull { it.kind == FmeManagedKind.TYPE && it.sourceBlockId == sourceId }
                ?.let { select(it.blockId) }
        }
    }

    private fun cancelRule() {
        replaceSourceId = null
        replaceGroup = null
    }

    private fun applyRule() {
        val source = replaceSourceId ?: return
        val key = contextKey(activeScope) ?: return
        val target = Fme.selectedBlockId()
        val group = replaceGroup
        val saved = if (group != null) Fme.setPositionReplacement(group, target) else Fme.setTypeReplacement(activeScope, key, source, target)
        if (!saved) return
        cancelRule()
        Toasts.show(tr("rule_saved", "Saved %s → %s.", displayName(source), displayName(target)), kind = ToastKind.SUCCESS)
    }

    private fun enterEditMode() {
        if (!Fme.startEditMode()) return
        UiManager.close()
    }

    private fun select(entry: BlockCatalogEntry) = select(entry.blockId)

    private fun select(blockId: String) {
        if (!Fme.selectBlock(blockId)) {
            Toasts.show(tr("selection_failed", "That block cannot be selected."), kind = ToastKind.ERROR)
        }
    }

    private fun selectTab(id: String) {
        activeScope = FmeScope.valueOf(id)
        lastActiveScope = activeScope
        Fme.selectScope(activeScope)
        pinnedContexts -= activeScope
        browser.reset()
        cancelRule()
    }

    private fun contextKey(scope: FmeScope): String? = when (scope) {
        FmeScope.GLOBAL -> ""
        else -> selectedContexts[scope] ?: Fme.currentScopeKey(scope)
    }

    private fun resetForDataChange() {
        selectedContexts.clear()
        pinnedContexts.clear()
        browser.reset()
        browser.invalidateBlocks()
        cancelRule()
    }

    // Right clicks on nothing else: tab, location row or workspace menu.
    override fun contentClicked(mouse: MouseButtonInput): Boolean {
        if (mouse.isRight) {
            tabBar.idAt(mouse.x, mouse.y)?.let { id ->
                openContextMenu(FmeScope.valueOf(id))
                return true
            }
            if (contextRow.has(mouse.x, mouse.y)) {
                openContextMenu(activeScope)
                return true
            }
        }
        if (super.contentClicked(mouse)) return true
        if (mouse.isRight && browser.contains(mouse.x, mouse.y)) {
            openWorkspaceMenu()
            return true
        }
        return false
    }

    companion object {
        private val tr = I18n.scope("blackaddons.fme")

        private var lastActiveScope = FmeScope.GLOBAL
        /** Lives longer than the screen, so rules can be pasted in another location. */
        private var copiedContext: CopiedContext? = null
        private const val HEADER_H = 22f
        private const val SEARCH_H = 24f
        private const val CONTEXT_H = 24f
        private const val CONTEXT_W = 250f
        private const val REPLACE_W = 130f
        private const val RULE_BAR_H = 30f
        private const val RULE_APPLY_W = 76f
        private const val RULE_CANCEL_W = 24f
        private const val EDIT_W = 76f
        private const val PROFILE_MIN_W = 80f
        private const val PROFILE_MAX_W = 160f
        private const val SELECTED_W = 190f
        fun open() = UiManager.push(FmeScreen())
    }

    private data class CopiedContext(val profileId: String, val scope: FmeScope, val key: String)
}
