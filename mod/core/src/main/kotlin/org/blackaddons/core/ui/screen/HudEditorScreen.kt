package org.blackaddons.core.ui.screen

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.hud.HudEditorMode
import org.blackaddons.api.hud.HudElement
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.BatchRect
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.FontChoice
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.*
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.font.FontCatalog
import org.blackaddons.core.log.Log
import java.awt.Color
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** HUD editor: drag to move, right-click to reset, scroll to scale. Esc saves. */
internal class HudEditorScreen : Panel() {

    /** Stays in Minecraft units, because it edits the real HUD elements. */
    override val normalizeScale: Boolean get() = false


    private var selected: HudElement? = null

    /** Read one frame late by [hitTest], so the label it just grew stays part of the hover. */
    private var hovered: HudElement? = null
    private var dragging = false
    private var resizing = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var gridSize = 10

    private class ElementAnims {
        val hover = Animation(durationMs = Theme.ANIM_HOVER.toLong())
        val select = Animation(durationMs = Theme.ANIM_HOVER.toLong())
    }

    private val anims = WeakHashMap<HudElement, ElementAnims>()

    private fun hoverAnim(hud: HudElement) = anims.getOrPut(hud) { ElementAnims() }.hover
    private fun selectAnim(hud: HudElement) = anims.getOrPut(hud) { ElementAnims() }.select

    // Captured at resize start, so a growing element can't move its own reference point with a CENTER/BOTTOM anchor.
    private var resizeLeft = 0f
    private var resizeTop = 0f
    private var resizeBaseW = 1f
    private var resizeBaseH = 1f

    override fun onShow() {
        HudEditorMode.active = true
    }

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val width = Platform.window.scaledWidth
        val height = Platform.window.scaledHeight
        val shiftHeld = UiKeys.shift
        Render.rect(0f, 0f, width, height, Color(0, 0, 0, 100))
        if (shiftHeld || alwaysGrid) drawGrid(width, height)

        fittedLine(I18n.tr("blackaddons.hud_editor.hint", gridSize), 8f, width, Theme.TEXT_SECONDARY, Fonts.regular)

        hovered = hitTest(mouseX, mouseY)

        /*
         * Update drag/resize at render rate so movement tracks the cursor every frame, not just when a mouseDragged
         * event fires.
         * SMOOOOOOOOOOOOOOOOOOOOOOOOOOTH
         * (does practically nothing, feel free to remove if we don't care about smoothness, I do OwO)
         */
        if (dragging) selected?.let { s ->
            val w = s.lastWidth
            val h = s.lastHeight
            val maxX = (width - w).coerceAtLeast(0f)
            val maxY = (height - h).coerceAtLeast(0f)
            val nx = mouseX - dragOffsetX
            val ny = mouseY - dragOffsetY
            val x = if (shiftHeld) snapHudToGrid(nx, maxX, gridSize) else nx.coerceIn(0f, maxX)
            val y = if (shiftHeld) snapHudToGrid(ny, maxY, gridSize) else ny.coerceIn(0f, maxY)
            s.setPosition(x, y)
        }
        if (resizing) selected?.let { s ->
            s.scaleX = ((mouseX - resizeLeft) / resizeBaseW).coerceIn(0.5f, 4f)
            s.scaleY = ((mouseY - resizeTop) / resizeBaseH).coerceIn(0.5f, 4f)
        }

        for (hud in editable) {
            hoverAnim(hud).animateTo(if (hud === hovered) 1f else 0f)
            selectAnim(hud).animateTo(if (hud === selected) 1f else 0f)

            // Drawn here, over the blur. The in-game HUD skips these while editing.
            preview(hud)
            drawOutline(hud.x, hud.y, hud.lastWidth, hud.lastHeight, outlineColor(hud))
        }

        // After every element, so no neighbor covers a label. Fades with the animation.
        for (hud in editable) {
            val vis = maxOf(hoverAnim(hud).get(), selectAnim(hud).get())
            if (vis <= 0f) continue
            drawLabel(hud, vis)
            val st = selectAnim(hud).get()
            if (st > 0f && hud.resizable) {
                Render.rect(
                    hud.x + hud.lastWidth - HANDLE, hud.y + hud.lastHeight - HANDLE,
                    HANDLE, HANDLE, Theme.ACCENT.withAlpha(st),
                )
            }
            // Outside the box, so it never covers small elements.
            if (st > 0f && hud.hasText) {
                val (tx, ty) = fontHandleRect(hud)
                Render.rect(tx, ty, FONT_HANDLE, FONT_HANDLE, Theme.ACCENT.withAlpha(st))
                val tw = Render.textWidth("T", FONT_HANDLE_TEXT, Fonts.bold)
                Render.text(
                    "T",
                    centered(tx, FONT_HANDLE, tw),
                    centered(ty, FONT_HANDLE, FONT_HANDLE_TEXT),
                    FONT_HANDLE_TEXT, Color.WHITE.withAlpha(st), Fonts.bold,
                )
            }
        }

        selected?.let { s ->
            val info = "${s.translatedName} · ${s.anchorX}/${s.anchorY} (${s.offsetX.toInt()}, ${s.offsetY.toInt()}) · ${s.lastWidth.toInt()}×${s.lastHeight.toInt()} · ${s.scaleX.formatDecimals()}×${s.scaleY.formatDecimals()} · ${fontName(s.fontOverride)}"
            fittedLine(info, 22f, width, Theme.ACCENT, Fonts.medium)
        }
    }

    private fun drawGrid(width: Float, height: Float) {
        val minor = mutableListOf<BatchRect>()
        val major = mutableListOf<BatchRect>()
        for (column in 0..(width / gridSize).toInt()) {
            val line = BatchRect(column * gridSize.toFloat(), 0f, 1f, height)
            (if (column % 5 == 0) major else minor).add(line)
        }
        for (row in 0..(height / gridSize).toInt()) {
            val line = BatchRect(0f, row * gridSize.toFloat(), width, 1f)
            (if (row % 5 == 0) major else minor).add(line)
        }
        Render.rects(minor, GRID_MINOR)
        Render.rects(major, GRID_MAJOR)
    }

    /** Shrunk to fit narrow windows. Chrome can be small but never cut off. */
    private fun fittedLine(text: String, y: Float, width: Float, color: Color, font: Font) {
        val room = (width - CHROME_PAD * 2f).coerceAtLeast(1f)
        val natural = Render.textWidth(text, Theme.FONT_SMALL, font)
        val size = if (natural <= room) Theme.FONT_SMALL else Theme.FONT_SMALL * (room / natural)
        val drawn = Render.textWidth(text, size, font)
        Render.text(text, (width - drawn) / 2f, y, size, color, font)
    }

    private fun outlineColor(hud: HudElement): Color {
        val ht = hoverAnim(hud).get()
        val st = selectAnim(hud).get()
        val hovered = lerpColor(OUTLINE_BASE, OUTLINE_HOVER, ht)
        return lerpColor(hovered, Theme.ACCENT, st)
    }

    private class LabelBox(val x: Float, val y: Float, val w: Float, val h: Float)

    /** Above the element, or below it near the top edge. Drawing, hit testing and the "T" handle all use this. */
    private fun labelRect(hud: HudElement): LabelBox {
        val h = Render.textHeight(Theme.FONT_SMALL, Fonts.medium)
        val w = Render.textWidth(hud.translatedName, Theme.FONT_SMALL, Fonts.medium)
        val above = hud.y - h - LABEL_GAP
        val y = if (above >= 0f) above else hud.y + hud.lastHeight + LABEL_GAP
        return LabelBox(hud.x + 2f, y, w, h)
    }

    private fun drawLabel(hud: HudElement, vis: Float) {
        val box = labelRect(hud)
        Render.rect(box.x - 2f, box.y - 1f, box.w + 4f, box.h + 2f, Theme.BACKGROUND.withAlpha(vis), Theme.RADIUS_SMALL)
        Render.text(hud.translatedName, box.x, box.y, Theme.FONT_SMALL, outlineColor(hud).withAlpha(vis), Fonts.medium)
    }

    private fun fontName(key: String): String = when (val choice = FontChoice.byName(key)) {
        FontChoice.INHERIT -> I18n.trOr("blackaddons.hud_editor.font_inherit", choice.displayName)
        null -> FontCatalog.installed().firstOrNull { it.key == key }?.displayName ?: key
        else -> choice.displayName
    }

    private fun openFontMenu(hud: HudElement) {
        fun item(key: String) = ContextMenu.Item(
            fontName(key),
            icon = LucideIcons.circleCheck.takeIf { key == hud.fontOverride },
        ) { hud.fontOverride = key }

        val installed = FontCatalog.installed().filter { it.isFile && !it.isBundled }
        UiManager.contextMenu(buildList {
            FontChoice.entries.forEach { add(item(it.name)) }
            if (installed.isNotEmpty()) add(ContextMenu.Separator)
            installed.forEach { add(item(it.key)) }
        })
    }

    private fun openOptionsMenu() {
        UiManager.contextMenu(listOf(
            ContextMenu.Item(I18n.tr("blackaddons.hud_editor.show_disabled"), checked = { showDisabled }) {
                showDisabled = !showDisabled
            },
            ContextMenu.Item(I18n.tr("blackaddons.hud_editor.always_grid"), checked = { alwaysGrid }) {
                alwaysGrid = !alwaysGrid
            },
            ContextMenu.Separator,
            ContextMenu.Item(
                I18n.tr("blackaddons.configs.reset_huds.button"),
                danger = true,
                confirm = I18n.tr("blackaddons.configs.reset_huds.label"),
            ) {
                FeatureManager.hudElements.forEach(HudElement::resetToDefault)
            },
        ))
    }

    private val broken = HashSet<HudElement>()

    /* A disabled element keeps its last size. Listing it would leave an invisible grab zone. */
    private val editable: List<HudElement>
        get() = if (showDisabled) FeatureManager.hudElements else FeatureManager.hudElements.filter { it.toggle }

    // A throwing addon preview shouldn't break the editor.
    private fun preview(hud: HudElement) {
        try {
            Render.isolated { hud.renderElement(true) }
        } catch (t: Throwable) {
            if (broken.add(hud)) Log.main.error("HUD '${hud.name}' threw while drawing its editor preview", t)
        }
    }

    private fun drawOutline(x: Float, y: Float, w: Float, h: Float, color: Color) {
        Render.rect(x, y, w, 1f, color)
        Render.rect(x, y + h - 1f, w, 1f, color)
        Render.rect(x, y, 1f, h, color)
        Render.rect(x + w - 1f, y, 1f, h, color)
        Render.rect(x, y, w, h, color.withAlpha(20))
    }

    private fun hitTest(mx: Float, my: Float): HudElement? {
        // Only the hovered or selected element's label is a hit target, and it wins over content.
        for (hud in listOfNotNull(selected, hovered)) {
            if (inLabel(hud, mx, my)) return hud
        }
        return editable.lastOrNull { hud ->
            mx in (hud.x - HIT_PAD)..(hud.x + hud.lastWidth + HIT_PAD) &&
                my in (hud.y - HIT_PAD)..(hud.y + hud.lastHeight + HIT_PAD)
        }
    }

    private fun inLabel(hud: HudElement, mx: Float, my: Float): Boolean {
        val box = labelRect(hud)
        return mx in (box.x - HIT_PAD)..(box.x + box.w + HIT_PAD) && my in box.y..(box.y + box.h)
    }

    private fun overHandle(mx: Float, my: Float, hud: HudElement): Boolean {
        if (!hud.resizable) return false
        val w = hud.lastWidth
        val h = hud.lastHeight
        return mx in (hud.x + w - HANDLE)..(hud.x + w) && my in (hud.y + h - HANDLE)..(hud.y + h)
    }

    private fun fontHandleRect(hud: HudElement): Pair<Float, Float> {
        val box = labelRect(hud)
        return (box.x + box.w + 4f) to (centered(box.y, box.h, FONT_HANDLE))
    }

    private fun overFontHandle(mx: Float, my: Float, hud: HudElement): Boolean {
        if (!hud.hasText) return false
        val (tx, ty) = fontHandleRect(hud)
        return hit(mx, my, tx, ty, FONT_HANDLE, FONT_HANDLE)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        val mx = mouse.x
        val my = mouse.y
        // The selected element's handle wins over selecting what's under it.
        if (mouse.isLeft) selected?.let { s ->
            if (overHandle(mx, my, s)) {
                resizing = true
                resizeLeft = s.x
                resizeTop = s.y
                resizeBaseW = (s.lastWidth / s.scaleX).coerceAtLeast(1f)
                resizeBaseH = (s.lastHeight / s.scaleY).coerceAtLeast(1f)
                // Pinned LEFT/TOP, so it grows toward the cursor. Anchored again on release.
                s.setPosition(resizeLeft, resizeTop, AnchorX.LEFT, AnchorY.TOP)
                return true
            }
        }
        selected?.let { s ->
            if ((mouse.isLeft || mouse.isRight) && overFontHandle(mx, my, s)) {
                if (mouse.isRight) s.fontOverride = FontChoice.INHERIT.name else openFontMenu(s)
                return true
            }
        }
        val hud = hitTest(mx, my)
        if (mouse.isRight && hud != null) {
            hud.resetToDefault()
            return true
        }
        if (mouse.isRight) {
            openOptionsMenu()
            return true
        }
        if (mouse.isLeft) {
            selected = hud
            if (hud != null) {
                dragging = true
                dragOffsetX = mx - hud.x
                dragOffsetY = my - hud.y
            }
            return true
        }
        return false
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        if (dragging || resizing) selected?.reanchorToNearest()
        dragging = false
        resizing = false
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        // render() updates positions. Only consume the event here.
        return dragging || resizing
    }

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (UiKeys.shift && wheel.amount != 0f) {
            gridSize = (gridSize + (if (wheel.amount > 0) 1 else -1)).coerceIn(2, 50)
            return true
        }
        val s = selected ?: return false
        if (!s.resizable) return true
        // Keeps any non-proportional ratio.
        val factor = (1f + wheel.amount * 0.1f).coerceAtLeast(0.1f)
        s.scaleX = (s.scaleX * factor).coerceIn(0.5f, 4f)
        s.scaleY = (s.scaleY * factor).coerceIn(0.5f, 4f)
        return true
    }

    override fun keyPressed(key: KeyInput): Boolean {
        if (key.code == Keys.ESCAPE) {
            UiManager.pop()
            return true
        }
        val s = selected ?: return false
        val step = if (key.shift) 5f else 1f
        when (key.code) {
            Keys.UP -> {
                s.offsetY -= step
                return true
            }
            Keys.DOWN -> {
                s.offsetY += step
                return true
            }
            Keys.LEFT -> {
                s.offsetX -= step
                return true
            }
            Keys.RIGHT -> {
                s.offsetX += step
                return true
            }
        }
        return false
    }

    override fun onClose() {
        HudEditorMode.active = false
        BlackaddonsConfig.save()
    }

    companion object {
        private const val CHROME_PAD = 6f

        private const val HANDLE = 6f

        private const val FONT_HANDLE = 8f

        private const val FONT_HANDLE_TEXT = 7f

        private const val HIT_PAD = 4f

        private const val LABEL_GAP = 2f

        // B: do we even need this atp?
        private val OUTLINE_BASE = Color(255, 255, 255, 110)

        private val OUTLINE_HOVER = Color(255, 255, 255, 190)

        private val GRID_MINOR = Color(255, 255, 255, 30)
        private val GRID_MAJOR = Color(255, 255, 255, 70)

        private var showDisabled = false
        private var alwaysGrid = false

        fun open(focus: HudElement? = null) {
            UiManager.push(HudEditorScreen().apply { selected = focus })
        }
    }
}

internal fun snapHudToGrid(position: Float, maxPosition: Float, gridSize: Int): Float {
    val lastLine = (maxPosition / gridSize).toInt() * gridSize
    return ((position / gridSize).roundToInt() * gridSize).coerceIn(0, lastLine).toFloat()
}
