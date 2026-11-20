package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.gui.GuiBox
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.render.Render
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
import kotlin.math.min
import kotlin.math.roundToInt

private val tr = I18n.scope("blackaddons.inventory_buttons")

internal object InventoryButtonEditor {

    private val selection = ArrayList<InventoryButton>()
    private var grabbed: InventoryButton? = null
    private var dragStarts: List<Pair<InventoryButton, GuiBox>> = emptyList()
    private var dragging = false
    private var resizing = false
    private var boxSelecting = false
    private var boxBase: List<InventoryButton> = emptyList()
    private var moved = false
    private var pressX = 0
    private var pressY = 0
    private var bandX = 0
    private var bandY = 0
    private var dragOffsetX = 0
    private var dragOffsetY = 0
    private var guideX: Int? = null
    private var guideY: Int? = null
    var alwaysGrid = false
    private var gridSize = DEFAULT_GRID
    private var iconDrag: IconDrag? = null

    private val history = EditHistory(
        capture = { InventoryButtonStore.buttons.map { it.copy() } to InventoryButtonStore.pencil.copy() },
        restore = { (buttons, pencil) -> InventoryButtonStore.restore(buttons, pencil) },
    )

    private val overlay get() = InventoryButtonsOverlay
    private val pencil get() = InventoryButtonStore.pencil

    fun dropTarget(hovered: InventoryButton?): InventoryButton? = hovered.takeIf { iconDrag?.released == false }

    // Kept across edit sessions, so a mistake noticed after leaving edit mode can still be undone.
    fun clearHistory() = history.clear()

    fun reset() {
        selection.clear()
        endPointer()
        iconDrag = null
    }

    fun click(metrics: ContainerMetrics, mouse: MouseButtonInput, mx: Int, my: Int) {
        val onPencil = overlay.pencilBox?.has(mx, my) == true
        val button = if (onPencil) null else overlay.buttonAt(mx, my)
        if (mouse.isRight) {
            if (button != null) {
                if (!isSelected(button)) select(button)
                InventoryButtonMenus.forButton(button, selectedButtons(), metrics, mouse)
            } else if (onPencil) {
                InventoryButtonMenus.forPencil(mouse)
            } else {
                InventoryButtonMenus.forEmpty(metrics, mouse, mx, my)
            }
            return
        }
        if (!mouse.isLeft) return
        pressX = mx
        pressY = my
        moved = false
        // The selected button's resize handle wins over whatever is under it.
        selection.singleOrNull()?.let(overlay::boxOf)?.let { box ->
            if (inHandle(mx, my, box)) {
                resizing = true
                return
            }
        }
        val target = if (onPencil) pencil else button
        if (target != null) {
            if (UiKeys.ctrl && target !== pencil) {
                toggle(target)
                return
            }
            if (!isSelected(target)) select(target)
            grab(target, mx, my)
            return
        }
        boxBase = if (UiKeys.ctrl) selectedButtons() else emptyList()
        selection.clear()
        selection += boxBase
        val raw = Platform.container.hoveredItemRaw()
        if (raw != null) {
            iconDrag = IconDrag(raw, mouse.x, mouse.y)
        } else {
            boxSelecting = true
            bandX = mx
            bandY = my
        }
    }

    // True when the pencil was clicked without being dragged, which ends edit mode.
    fun release(mouse: MouseButtonInput): Boolean {
        val pencilClicked = dragging && grabbed === pencil && !moved
        if (dragging && !moved && grabbed !== pencil) grabbed?.let(::select)
        if ((dragging || resizing) && moved) InventoryButtonStore.save()
        endPointer()
        iconDrag?.takeIf { !it.released }?.let { drop(it, mouse.x.toInt(), mouse.y.toInt()) }
        return pencilClicked
    }

    // Runs every frame, like the HUD editor, so the button follows the cursor smoothly.
    fun followPointer(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        if (!dragging && !resizing && !boxSelecting) return
        if (!moved) {
            // A plain click shouldn't move anything.
            if (abs(mouseX - pressX) + abs(mouseY - pressY) < DRAG_THRESHOLD) return
            moved = true
            if (!boxSelecting) history.remember()
        }
        when {
            boxSelecting -> selectInBand(mouseX, mouseY)
            dragging -> drag(metrics, mouseX, mouseY)
            else -> resize(metrics, mouseX, mouseY)
        }
    }

    fun scroll(metrics: ContainerMetrics, wheel: MouseScrollInput): Boolean {
        if (wheel.amount == 0f) return false
        if (UiKeys.shift) {
            gridSize = (gridSize + if (wheel.amount > 0) 1 else -1).coerceIn(MIN_GRID, MAX_GRID)
            return true
        }
        if (selection.isEmpty()) return false
        history.remember(SCROLL_GROUP)
        val factor = 1f + wheel.amount * SCROLL_SCALE_STEP
        for (s in selection) {
            val box = overlay.boxOf(s) ?: continue
            s.width = (s.width * factor).roundToInt().coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE)
            s.height = (s.height * factor).roundToInt().coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE)
            settle(s, box.copy(w = s.width, h = s.height), metrics)
        }
        InventoryButtonStore.save()
        return true
    }

    fun key(metrics: ContainerMetrics, key: KeyInput): Boolean {
        if (key.ctrl && key.code == Keys.Z) {
            undo()
            return true
        }
        val buttons = selectedButtons()
        if (key.code == Keys.DELETE || key.code == Keys.BACKSPACE) {
            if (buttons.isEmpty()) return false
            delete(buttons)
            return true
        }
        if (key.ctrl && key.code == Keys.D) {
            if (buttons.isEmpty()) return false
            duplicate(buttons, metrics)
            return true
        }
        if (selection.isEmpty()) return false
        val step = if (key.shift) NUDGE_FAST else 1
        val (dx, dy) = when (key.code) {
            Keys.UP -> 0 to -step
            Keys.DOWN -> 0 to step
            Keys.LEFT -> -step to 0
            Keys.RIGHT -> step to 0
            else -> return false
        }
        history.remember(NUDGE_GROUP)
        for (s in selection) {
            val box = overlay.boxOf(s) ?: continue
            settle(s, box.copy(x = box.x + dx, y = box.y + dy), metrics)
        }
        InventoryButtonStore.save()
        return true
    }

    fun remember() = history.remember()

    fun undo() {
        if (!history.undo()) return
        reset()
        ButtonPainter.clearPops()
        InventoryButtonStore.save()
    }

    fun selectOnly(buttons: List<InventoryButton>) {
        selection.clear()
        selection += buttons
    }

    fun create(metrics: ContainerMetrics, mx: Int, my: Int) {
        history.remember()
        val button = newButtonHere()
        val size = InventoryButton.DEFAULT_SIZE
        settle(button, GuiBox(mx - size / 2, my - size / 2, size, size), metrics)
        InventoryButtonStore.buttons += button
        InventoryButtonStore.save()
        select(button)
        openEditor(button, placeButton(button, metrics.gui, metrics.width, metrics.height))
    }

    // Outside the inventory, a new button belongs to this container, so it doesn't vanish the moment it's made.
    private fun newButtonHere(): InventoryButton {
        if (Platform.container.isPlayerInventoryOpen()) return InventoryButton()
        val title = Platform.container.title()?.trim().orEmpty()
        return if (title.isEmpty()) InventoryButton(scope = ButtonScope.ALL_CONTAINERS) else InventoryButton(scope = ButtonScope.TITLE, titleFilter = title)
    }

    // Copies go right of the whole group, so a duplicated row doesn't land on itself.
    fun duplicate(buttons: List<InventoryButton>, metrics: ContainerMetrics) {
        val boxes = buttons.mapNotNull { b -> overlay.boxOf(b)?.let { b to it } }
        if (boxes.isEmpty()) return
        history.remember()
        val dx = boxes.maxOf { it.second.right } - boxes.minOf { it.second.x } + DUPLICATE_GAP
        val copies = boxes.map { (button, box) -> button.copy().also { settle(it, box.copy(x = box.x + dx), metrics) } }
        InventoryButtonStore.buttons += copies
        InventoryButtonStore.save()
        selectOnly(copies)
    }

    fun delete(buttons: List<InventoryButton>) {
        if (buttons.isEmpty()) return
        history.remember()
        InventoryButtonStore.buttons.removeIf { b -> buttons.any { it === b } }
        selection.removeIf { s -> buttons.any { it === s } }
        ButtonPainter.forget(buttons)
        InventoryButtonStore.save()
    }

    fun openEditor(button: InventoryButton, at: GuiBox? = overlay.boxOf(button)) {
        val box = at ?: return
        history.remember()
        // Popovers use normalized panel units; this overlay uses GUI units.
        val scale = UiManager.normalizedScale
        UiManager.overlay(InventoryButtonPopover(button, box.x / scale, box.y / scale, box.w / scale, box.h / scale))
    }

    private fun isSelected(button: InventoryButton) = selection.any { it === button }

    private fun selectedButtons(): List<InventoryButton> = selection.filter { it !== pencil }

    private fun select(button: InventoryButton) {
        selection.clear()
        selection += button
    }

    private fun toggle(button: InventoryButton) {
        selection.removeIf { it === pencil }
        if (!selection.removeIf { it === button }) selection += button
    }

    private fun grab(target: InventoryButton, mx: Int, my: Int) {
        val box = overlay.boxOf(target) ?: return
        grabbed = target
        dragging = true
        dragOffsetX = mx - box.x
        dragOffsetY = my - box.y
        dragStarts = selection.mapNotNull { b -> overlay.boxOf(b)?.let { b to it } }
    }

    private fun endPointer() {
        dragging = false
        resizing = false
        boxSelecting = false
        grabbed = null
        dragStarts = emptyList()
        guideX = null
        guideY = null
    }

    private fun selectInBand(mouseX: Int, mouseY: Int) {
        bandX = mouseX
        bandY = mouseY
        val band = band()
        selectOnly(boxBase + overlay.placed.filter { (b, box) -> box.intersects(band) && boxBase.none { it === b } }.map { it.first })
    }

    private fun band() = GuiBox(min(pressX, bandX), min(pressY, bandY), abs(bandX - pressX), abs(bandY - pressY))

    private fun drag(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        val grabbed = grabbed ?: return
        val start = dragStarts.firstOrNull { it.first === grabbed }?.second ?: return
        val gui = metrics.gui
        var x = mouseX - dragOffsetX
        var y = mouseY - dragOffsetY
        guideX = null
        guideY = null
        if (UiKeys.shift) {
            x = snapToGrid(x, gui.x, gridSize)
            y = snapToGrid(y, gui.y, gridSize)
        } else if (InventoryButtonStore.snapToButtons != UiKeys.alt) {
            val snapped = snapToNeighbors(start.copy(x = x, y = y), snapTargets(gui), SNAP_REACH)
            x = snapped.x
            y = snapped.y
            guideX = snapped.guideX
            guideY = snapped.guideY
        }
        val dx = x - start.x
        val dy = y - start.y
        for ((button, box) in dragStarts) settle(button, box.copy(x = box.x + dx, y = box.y + dy), metrics)
    }

    private fun snapTargets(gui: GuiBox): List<GuiBox> =
        (overlay.placed + listOfNotNull(overlay.pencilBox?.let { pencil to it }))
            .filter { (button, _) -> !isSelected(button) }
            .map { it.second } + gui

    private fun resize(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        val s = selection.singleOrNull() ?: return
        val box = overlay.boxOf(s) ?: return
        var w = mouseX - box.x
        var h = mouseY - box.y
        if (UiKeys.shift) {
            w = snapToGrid(w, 0, gridSize)
            h = snapToGrid(h, 0, gridSize)
        }
        s.width = w.coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE)
        s.height = h.coerceIn(InventoryButton.MIN_SIZE, InventoryButton.MAX_SIZE)
        settle(s, box.copy(w = s.width, h = s.height), metrics)
    }

    private fun settle(button: InventoryButton, at: GuiBox, metrics: ContainerMetrics) =
        settleButton(button, at, metrics.gui, metrics.width, metrics.height)

    private fun inHandle(mx: Int, my: Int, box: GuiBox): Boolean =
        GuiBox(box.right - HANDLE - HANDLE_HIT_PAD, box.bottom - HANDLE - HANDLE_HIT_PAD, HANDLE + HANDLE_HIT_PAD * 2, HANDLE + HANDLE_HIT_PAD * 2)
            .has(mx, my)

    private fun drop(drag: IconDrag, mx: Int, my: Int) {
        val target = overlay.buttonAt(mx, my)
        val icon = target?.let { ButtonIcons.fromRaw(drag.raw) }
        if (target == null || icon == null) {
            drag.release(null)
            return
        }
        history.remember()
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
        val single = selection.singleOrNull()
        val outlined = overlay.placed + listOfNotNull(overlay.pencilBox?.let { pencil to it })
        for ((button, box) in outlined) {
            val color = when {
                isSelected(button) -> Theme.ACCENT
                button === hovered -> Theme.TEXT_SECONDARY
                else -> continue
            }
            Render.hollowRect(box.x.toFloat(), box.y.toFloat(), box.w.toFloat(), box.h.toFloat(), 1f, color, ButtonPainter.radius)
            if (button === single) {
                Render.rect((box.right - HANDLE).toFloat(), (box.bottom - HANDLE).toFloat(), HANDLE.toFloat(), HANDLE.toFloat(), Theme.ACCENT, 1f)
            }
        }
        val width = metrics.width.toFloat()
        val height = metrics.height.toFloat()
        val guide = Theme.ACCENT.withAlpha(GUIDE_ALPHA)
        guideX?.let { Render.rect(it.toFloat(), 0f, 1f, height, guide) }
        guideY?.let { Render.rect(0f, it.toFloat(), width, 1f, guide) }
        if (boxSelecting && moved) {
            val band = band()
            Render.rect(band.x.toFloat(), band.y.toFloat(), band.w.toFloat(), band.h.toFloat(), Theme.ACCENT.withAlpha(BAND_FILL_ALPHA))
            Render.hollowRect(band.x.toFloat(), band.y.toFloat(), band.w.toFloat(), band.h.toFloat(), 1f, Theme.ACCENT)
        }
        centeredLine(
            tr("hint", "Drag to move · corner to resize · Shift: grid (scroll to size) · right-click: menu · drag an item onto a button for its icon · Esc: done"),
            HINT_Y, width, Theme.TEXT_SECONDARY,
        )
        centeredLine(
            tr("hint_select", "Ctrl+click or drag a box: select several · Alt: toggle snapping to buttons · Del: delete · Ctrl+D: duplicate · Ctrl+Z: undo"),
            HINT_SELECT_Y, width, Theme.TEXT_SECONDARY,
        )
        if (selection.size > 1) {
            centeredLine(tr("selected", "%d selected", selection.size), INFO_Y, width, Theme.ACCENT)
        } else {
            single?.let { s -> centeredLine("${s.width}×${s.height} · ${anchorName(s)} (${s.x}, ${s.y})", INFO_Y, width, Theme.ACCENT) }
        }
        iconDrag?.let { if (!it.draw(dt, mouseX.toFloat(), mouseY.toFloat())) iconDrag = null }
    }

    private fun anchorName(s: InventoryButton): String = when {
        s.anchorRight && s.anchorBottom -> tr("anchor.bottom_right", "bottom right")
        s.anchorRight -> tr("anchor.top_right", "top right")
        s.anchorBottom -> tr("anchor.bottom_left", "bottom left")
        else -> tr("anchor.top_left", "top left")
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
    private const val NUDGE_GROUP = "nudge"
    private const val SCROLL_GROUP = "scroll"
    private const val SCROLL_SCALE_STEP = 0.1f
    private const val SNAP_REACH = 3
    private const val DUPLICATE_GAP = 2
    private const val DRAG_THRESHOLD = 3
    private const val HANDLE = 4
    private const val HANDLE_HIT_PAD = 2
    private const val EDIT_DIM_ALPHA = 70
    private const val GRID_ALPHA = 0.08f
    private const val GUIDE_ALPHA = 0.6f
    private const val BAND_FILL_ALPHA = 0.12f
    private const val HINT_Y = 4f
    private const val HINT_SELECT_Y = 15f
    private const val INFO_Y = 26f
    private const val LINE_SIZE = 8f
    private const val LINE_MARGIN = 4f
}
