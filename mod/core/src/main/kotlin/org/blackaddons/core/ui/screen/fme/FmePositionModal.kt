package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.core.features.impl.visual.fme.Fme
import org.blackaddons.core.features.impl.visual.fme.FmeManagedGroup
import org.blackaddons.core.features.impl.visual.fme.FmePositionKey
import org.blackaddons.core.features.impl.visual.fme.FmeScope

internal class FmePositionModal(private val group: FmeManagedGroup) : ModalPanel(tr("positions_title", "Edited positions")) {

    override val panelWidth: Float get() = fitWidth(440f, min = 320f)
    override val panelHeight: Float
        get() = minOf(maxOf(154f, 100f + minOf(positions.size, 6) * (ROW_H + 2f)), (UiManager.screenHeight - 40f).coerceAtLeast(154f))

    private val positions = Fme.positions(group, LIST_LIMIT).toMutableList()
    private val hoverAnimations = mutableMapOf<FmePositionKey, Animation>()
    private val list = widgets.add(ScrollableList<FmePositionKey>().apply {
        rowHeight = ROW_H
        rowGap = 2f
        contentInsetY = 4f
        emptyMessage = tr("positions_empty", "No positions remain.")
        drawRow = { key, _, x, y, w -> drawPosition(key, x, y, w) }
    })
    private val addButton = widgets.add(Button(tr("add_position", "Add position")) { addPosition() }.apply { accent = true })
    private val doneButton = widgets.add(Button(I18n.trOr("blackaddons.list.done", "Done")) { UiManager.pop() }.apply { accent = true })

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val footerY = contentY + contentH - BUTTON_H
        val listH = footerY - Theme.PADDING - contentY
        // Right click on empty list space. Rows drawn after this sit on top.
        widgets.clickable(contentX, contentY, contentW, listH, Keys.MOUSE_RIGHT) { openListMenu() }
        val hidden = group.count - positions.size
        val listTop = if (hidden > 0) contentY + HIDDEN_NOTE_H else contentY
        if (hidden > 0) {
            Render.text(tr("positions_hidden", "Showing %d of %d positions.", positions.size, group.count), contentX, contentY, TextStyle.SMALL_MUTED)
        }
        list.items = positions
        list.render(contentX, listTop, contentW, footerY - Theme.PADDING - listTop)

        addButton.enabled = positions.isNotEmpty()
        addButton.render(contentX, footerY, ADD_W, BUTTON_H, mouseX, mouseY)
        doneButton.render(contentX + contentW - 76f, footerY, 76f, BUTTON_H, mouseX, mouseY)
    }

    private fun drawPosition(key: FmePositionKey, x: Float, y: Float, w: Float) {
        val world = Fme.worldPosition(key)
        val label = when {
            key.scope == FmeScope.DUNGEON && world != null -> tr(
                "position_world_local", "%d, %d, %d (local %d, %d, %d)",
                world.x, world.y, world.z, key.x, key.y, key.z,
            )
            key.scope == FmeScope.DUNGEON ->
                tr("position_local", "Local: %d, %d, %d", key.x, key.y, key.z)
            else -> "%d, %d, %d".format(key.x, key.y, key.z)
        }
        val hovered = widgets.clickable(x, y, w, ROW_H) { edit(key) }
        widgets.clickable(x, y, w, ROW_H, Keys.MOUSE_RIGHT) { openMenu(key) }
        val hover = hoverAnimations.getOrPut(key) { Animation(durationMs = Theme.ANIM_HOVER.toLong()) }.also {
            it.animateTo(if (hovered) 1f else 0f)
        }.get()
        Render.rect(x, y, w, ROW_H, lerpColor(Theme.SURFACE.withAlpha(90), Theme.SURFACE_HOVER, hover), Theme.RADIUS_SMALL)
        Render.text(
            label,
            x + 6f, Render.centerY(y, ROW_H, TextStyle.SMALL_SECONDARY),
            TextStyle.SMALL_SECONDARY, maxWidth = w - 12f,
        )
    }

    private fun edit(key: FmePositionKey) {
        UiManager.push(FmePositionEditModal(key) { moved ->
            val index = positions.indexOf(key)
            if (index >= 0) positions[index] = moved
            hoverAnimations.remove(key)?.let { hoverAnimations[moved] = it }
        })
    }

    private fun openMenu(key: FmePositionKey) {
        UiManager.contextMenu(listOf(
            ContextMenu.Item(tr("edit_position", "Edit position"), LucideIcons.settings) { edit(key) },
            ContextMenu.Item(tr("copy_coordinates", "Copy coordinates"), LucideIcons.copy) {
                Platform.clipboard.copy(copyText(key))
            },
            ContextMenu.Item(tr("paste_coordinates", "Paste coordinates"), LucideIcons.clipboardPaste) {
                pasteCoordinates(key)
            },
            ContextMenu.Separator,
            ContextMenu.Item(
                tr("remove_position", "Remove position"),
                LucideIcons.circleX,
                danger = true,
                confirm = tr("remove_position_confirm", "Remove this position edit?"),
            ) { remove(key) },
        ))
    }

    private fun openListMenu() {
        val group = currentGroup()
        UiManager.contextMenu(buildList {
            add(ContextMenu.Item(tr("add_position", "Add position"), LucideIcons.actions) { addPosition() })
            Fme.nearestPosition(group)?.let { nearest ->
                add(ContextMenu.Item(tr("remove_nearest", "Remove nearest"), LucideIcons.circleX, danger = true) {
                    this@FmePositionModal.remove(nearest)
                })
            }
            if (positions.isNotEmpty()) {
                add(ContextMenu.Item(
                    tr("remove_all", "Remove all"), LucideIcons.circleX, danger = true,
                    confirm = tr("remove_all_confirm", "Remove all %d positions?", group.count),
                ) { this@FmePositionModal.removeAll() })
            }
        })
    }

    private fun addPosition() {
        val group = currentGroup()
        if (group.ruleKeys.isEmpty()) return
        UiManager.push(FmePositionEditModal(group) { added -> positions += added })
    }

    private fun pasteCoordinates(key: FmePositionKey) {
        val coordinates = parseFmeCoordinates(Platform.clipboard.paste())
        if (coordinates == null) {
            Toasts.show(
                tr("coordinates_invalid", "Clipboard does not contain three whole-number coordinates."),
                kind = ToastKind.ERROR,
            )
            return
        }
        UiManager.push(FmePositionEditModal(key, coordinates) { moved ->
            val index = positions.indexOf(key)
            if (index >= 0) positions[index] = moved
        })
    }

    private fun remove(key: FmePositionKey) {
        if (Fme.removePosition(key)) {
            positions.remove(key)
            hoverAnimations.remove(key)
        }
    }

    private fun removeAll() {
        Fme.removeGroup(currentGroup())
        positions.clear()
        hoverAnimations.clear()
    }

    private fun currentGroup() = Fme.managedGroups(group.scope, group.scopeKey)
        .firstOrNull { it.kind == group.kind && it.ruleKeys.any(group.ruleKeys::contains) } ?: group

    private fun copyText(key: FmePositionKey): String = "${key.x}, ${key.y}, ${key.z}"

    private companion object {
        val tr = I18n.scope("blackaddons.fme")

        const val ROW_H = 24f
        const val BUTTON_H = 22f
        const val ADD_W = 92f
        const val LIST_LIMIT = 500
        const val HIDDEN_NOTE_H = 16f
    }
}
