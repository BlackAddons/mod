package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.gui.GuiBox
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.UiKeys
import org.blackaddons.api.util.I18n
import java.awt.Color
import kotlin.math.abs
import kotlin.math.roundToInt

private val tr = I18n.scope("blackaddons.inventory_buttons")

internal object InventoryButtonEditor {

    private var selected: InventoryButton? = null
    private var dragging = false
    private var resizing = false
    private var moved = false
    private var pressX = 0
    private var pressY = 0
    private var dragOffsetX = 0
    private var dragOffsetY = 0
    private var alwaysGrid = false
    private var gridSize = DEFAULT_GRID
    private var iconDrag: IconDrag? = null

    private val overlay get() = InventoryButtonsOverlay
    private val pencil get() = InventoryButtonStore.pencil

    fun dropTarget(hovered: InventoryButton?): InventoryButton? = hovered.takeIf { iconDrag?.released == false }

    fun reset() {
        selected = null
        dragging = false
        resizing = false
        iconDrag = null
    }

    fun click(metrics: ContainerMetrics, mouse: MouseButtonInput, mx: Int, my: Int) {
        val onPencil = overlay.pencilBox?.has(mx, my) == true
        val button = if (onPencil) null else overlay.buttonAt(mx, my)
        if (mouse.isRight) {
            if (button != null) {
                selected = button
                buttonMenu(button, metrics, mouse)
            } else if (!onPencil) {
                emptyMenu(metrics, mouse, mx, my)
            }
            return
        }
        if (!mouse.isLeft) return
        pressX = mx
        pressY = my
        moved = false
        // The selected button's resize handle wins over whatever is under it.
        selected?.let(overlay::boxOf)?.let { box ->
            if (inHandle(mx, my, box)) {
                resizing = true
                return
            }
        }
        val grabbed = if (onPencil) pencil else button
        if (grabbed != null) {
            selected = grabbed
            dragging = true
            val box = overlay.boxOf(grabbed) ?: return
            dragOffsetX = mx - box.x
            dragOffsetY = my - box.y
            return
        }
        selected = null
        Platform.container.hoveredItemRaw()?.let { iconDrag = IconDrag(it, mouse.x, mouse.y) }
    }

    // True when the pencil was clicked without being dragged, which ends edit mode.
    fun release(mouse: MouseButtonInput): Boolean {
        val pencilClicked = dragging && selected === pencil && !moved
        if ((dragging || resizing) && moved) InventoryButtonStore.save()
        dragging = false
        resizing = false
        iconDrag?.takeIf { !it.released }?.let { drop(it, mouse.x.toInt(), mouse.y.toInt()) }
        return pencilClicked
    }

    // Runs every frame, like the HUD editor, so the button follows the cursor smoothly.
    fun followPointer(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        val s = selected ?: return
        if (!dragging && !resizing) return
        if (!moved) {
            // A plain click shouldn't move anything.
            if (abs(mouseX - pressX) + abs(mouseY - pressY) < DRAG_THRESHOLD) return
            moved = true
        }
        val box = overlay.boxOf(s) ?: return
        val gui = metrics.gui
        val snap = UiKeys.shift
        if (dragging) {
            var x = mouseX - dragOffsetX
            var y = mouseY - dragOffsetY
            if (snap) {
                x = snapToGrid(x, gui.x, gridSize)
                y = snapToGrid(y, gui.y, gridSize)
            }
            settle(s, box.copy(x = x, y = y), metrics)
        } else {
            var w = mouseX - box.x
            var h = mouseY - box.y
            if (snap) {
                w = snapToGrid(w, 0, gridSize)
                h = snapToGrid(h, 0, gridSize)
            }
            s.width = w.coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE)
            s.height = h.coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE)
            settle(s, box.copy(w = s.width, h = s.height), metrics)
        }
    }

    fun scroll(metrics: ContainerMetrics, wheel: MouseScrollInput): Boolean {
        if (wheel.amount == 0f) return false
        if (UiKeys.shift) {
            gridSize = (gridSize + if (wheel.amount > 0) 1 else -1).coerceIn(MIN_GRID, MAX_GRID)
            return true
        }
        val s = selected ?: return false
        val box = overlay.boxOf(s) ?: return false
        val factor = 1f + wheel.amount * SCROLL_SCALE_STEP
        s.width = (s.width * factor).roundToInt().coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE)
        s.height = (s.height * factor).roundToInt().coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE)
        settle(s, box.copy(w = s.width, h = s.height), metrics)
        InventoryButtonStore.save()
        return true
    }

    fun key(metrics: ContainerMetrics, key: KeyInput): Boolean {
        val s = selected ?: return false
        val box = overlay.boxOf(s) ?: return false
        val step = if (key.shift) NUDGE_FAST else 1
        val moved = when (key.code) {
            Keys.UP -> box.copy(y = box.y - step)
            Keys.DOWN -> box.copy(y = box.y + step)
            Keys.LEFT -> box.copy(x = box.x - step)
            Keys.RIGHT -> box.copy(x = box.x + step)
            else -> return false
        }
        settle(s, moved, metrics)
        InventoryButtonStore.save()
        return true
    }

    private fun settle(button: InventoryButton, at: GuiBox, metrics: ContainerMetrics) =
        settleButton(button, at, metrics.gui, metrics.width, metrics.height)

    private fun inHandle(mx: Int, my: Int, box: GuiBox): Boolean =
        GuiBox(box.right - HANDLE - HANDLE_HIT_PAD, box.bottom - HANDLE - HANDLE_HIT_PAD, HANDLE + HANDLE_HIT_PAD * 2, HANDLE + HANDLE_HIT_PAD * 2)
            .has(mx, my)

    private fun buttonMenu(button: InventoryButton, metrics: ContainerMetrics, mouse: MouseButtonInput) {
        UiManager.contextMenuOver(mouse.x, mouse.y, listOf(
            ContextMenu.Item(tr("menu.edit", "Edit..."), icon = LucideIcons.pencil) { openEditor(button) },
            ContextMenu.Item(tr("menu.duplicate", "Duplicate"), icon = LucideIcons.copy) { duplicate(button, metrics) },
            ContextMenu.Separator,
            ContextMenu.Item(
                tr("menu.delete", "Delete"),
                danger = true,
                confirm = tr("menu.delete_confirm", "Delete this button?"),
            ) { delete(button) },
        ))
    }

    private fun emptyMenu(metrics: ContainerMetrics, mouse: MouseButtonInput, mx: Int, my: Int) {
        val here = overlay.placed.map { it.first }
        UiManager.contextMenuOver(mouse.x, mouse.y, listOf(
            ContextMenu.Item(tr("menu.new", "New button here")) { create(metrics, mx, my) },
            ContextMenu.Item(tr("menu.always_grid", "Always show grid"), checked = { alwaysGrid }) { alwaysGrid = !alwaysGrid },
            ContextMenu.Separator,
            ContextMenu.Item(
                tr("menu.remove_all", "Remove all buttons here"),
                danger = true,
                enabled = here.isNotEmpty(),
                confirm = tr("menu.remove_all_confirm", "Remove every button shown on this screen?"),
            ) { removeAll(here) },
        ))
    }

    private fun create(metrics: ContainerMetrics, mx: Int, my: Int) {
        val button = InventoryButton()
        val size = InventoryButton.DEFAULT_SIZE
        settle(button, GuiBox(mx - size / 2, my - size / 2, size, size), metrics)
        InventoryButtonStore.buttons += button
        InventoryButtonStore.save()
        selected = button
        openEditor(button, placeButton(button, metrics.gui, metrics.width, metrics.height))
    }

    private fun duplicate(button: InventoryButton, metrics: ContainerMetrics) {
        val box = overlay.boxOf(button) ?: return
        val copy = button.copy()
        settle(copy, box.copy(x = box.right + DUPLICATE_GAP), metrics)
        InventoryButtonStore.buttons += copy
        InventoryButtonStore.save()
        selected = copy
    }

    private fun delete(button: InventoryButton) {
        InventoryButtonStore.buttons.removeIf { it === button }
        if (selected === button) selected = null
        InventoryButtonStore.save()
    }

    private fun removeAll(buttons: List<InventoryButton>) {
        InventoryButtonStore.buttons.removeIf { b -> buttons.any { it === b } }
        selected = null
        InventoryButtonStore.save()
    }

    private fun openEditor(button: InventoryButton, at: GuiBox? = overlay.boxOf(button)) {
        val box = at ?: return
        // Popovers use normalized panel units; this overlay uses GUI units.
        val scale = UiManager.normalizedScale
        UiManager.overlay(InventoryButtonPopover(button, box.x / scale, box.y / scale, box.w / scale, box.h / scale))
    }

    private fun drop(drag: IconDrag, mx: Int, my: Int) {
        val target = overlay.buttonAt(mx, my)
        val icon = target?.let { ButtonIcons.fromRaw(drag.raw) }
        if (target == null || icon == null) {
            drag.release(null)
            return
        }
        target.icon = icon
        InventoryButtonStore.save()
        ButtonPainter.pop(target)
        drag.release(overlay.boxOf(target))
    }

    // The dim covers the inventory too, since its slots ignore clicks while editing.
    fun drawBackdrop(metrics: ContainerMetrics) {
        val w = metrics.width.toFloat()
        val h = metrics.height.toFloat()
        Render.rect(0f, 0f, w, h, Color(0, 0, 0, EDIT_DIM_ALPHA))
        if (!UiKeys.shift && !alwaysGrid) return
        val gui = metrics.gui
        val color = Color.WHITE.withAlpha(GRID_ALPHA)
        var gx = gui.x % gridSize
        while (gx < w) {
            Render.rect(gx.toFloat(), 0f, 1f, h, color)
            gx += gridSize
        }
        var gy = gui.y % gridSize
        while (gy < h) {
            Render.rect(0f, gy.toFloat(), w, 1f, color)
            gy += gridSize
        }
    }

    fun drawChrome(metrics: ContainerMetrics, hovered: InventoryButton?, dt: Float, mouseX: Int, mouseY: Int) {
        val outlined = overlay.placed + listOfNotNull(overlay.pencilBox?.let { pencil to it })
        for ((button, box) in outlined) {
            val color = when {
                button === selected -> Theme.ACCENT
                button === hovered -> Theme.TEXT_SECONDARY
                else -> continue
            }
            Render.hollowRect(box.x.toFloat(), box.y.toFloat(), box.w.toFloat(), box.h.toFloat(), 1f, color, ButtonPainter.RADIUS)
            if (button === selected) {
                Render.rect((box.right - HANDLE).toFloat(), (box.bottom - HANDLE).toFloat(), HANDLE.toFloat(), HANDLE.toFloat(), Theme.ACCENT, 1f)
            }
        }
        val width = metrics.width.toFloat()
        centeredLine(
            tr("hint", "Drag to move · corner to resize · Shift: grid (scroll to size) · right-click: menu · drag an item onto a button for its icon · Esc: done"),
            HINT_Y, width, Theme.TEXT_SECONDARY,
        )
        selected?.let { s ->
            val anchor = when {
                s.anchorRight && s.anchorBottom -> tr("anchor.bottom_right", "bottom right")
                s.anchorRight -> tr("anchor.top_right", "top right")
                s.anchorBottom -> tr("anchor.bottom_left", "bottom left")
                else -> tr("anchor.top_left", "top left")
            }
            centeredLine("${s.width}×${s.height} · $anchor (${s.x}, ${s.y})", INFO_Y, width, Theme.ACCENT)
        }
        iconDrag?.let { if (!it.draw(dt, mouseX.toFloat(), mouseY.toFloat())) iconDrag = null }
    }

    private fun centeredLine(text: String, y: Float, width: Float, color: Color) {
        val maxW = (width - LINE_MARGIN * 2f).coerceAtLeast(0f)
        val textW = Render.textWidth(text, LINE_SIZE, Fonts.regular).coerceAtMost(maxW)
        Render.text(text, (width - textW) / 2f, y, LINE_SIZE, color, Fonts.regular, maxW)
    }

    private const val DEFAULT_GRID = 10
    private const val MIN_GRID = 2
    private const val MAX_GRID = 50
    private const val NUDGE_FAST = 5
    private const val SCROLL_SCALE_STEP = 0.1f
    private const val DUPLICATE_GAP = 2
    private const val DRAG_THRESHOLD = 3
    private const val HANDLE = 4
    private const val HANDLE_HIT_PAD = 2
    private const val EDIT_DIM_ALPHA = 70
    private const val GRID_ALPHA = 0.08f
    private const val HINT_Y = 4f
    private const val INFO_Y = 15f
    private const val LINE_SIZE = 8f
    private const val LINE_MARGIN = 4f
}
