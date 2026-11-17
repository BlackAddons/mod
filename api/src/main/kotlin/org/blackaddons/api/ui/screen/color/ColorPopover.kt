package org.blackaddons.api.ui.screen.color
import org.blackaddons.api.feature.settings.impl.ChromaMode
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Popover
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.util.I18n
import java.awt.Color

/**
 * Compact picker pinned to its control; the expand button hands off to [ColorPickerScreen]. No dim, so the
 * screen behind previews live. Click-away, Esc or Enter closes.
 */
class ColorPopover(
    initial: Color,
    private val allowAlpha: Boolean = false,
    private val anchorX: Float = UiMouse.x,
    private val anchorY: Float = UiMouse.y,
    private val anchorW: Float = 0f,
    private val anchorH: Float = 0f,
    private val supportsChroma: Boolean = false,
    private val supportsPerCharMode: Boolean = false,
    initialChroma: Boolean = false,
    initialChromaSpeed: Float = 0.5f,
    initialChromaMode: ChromaMode = ChromaMode.ALL,
    /** Non-null shows a reset button. */
    private val default: Color? = null,
    private val defaultChroma: Boolean = false,
    private val defaultChromaSpeed: Float = 0.5f,
    private val defaultChromaMode: ChromaMode = ChromaMode.ALL,
    private val onChromaChange: (chroma: Boolean, speed: Float, mode: ChromaMode) -> Unit = { _, _, _ -> },
    private val onChange: (Color) -> Unit,
) : Popover() {

    private val area = widgets.add(ColorArea(initial, allowAlpha, onChange, svSize = SV_SIZE))
    private val hex = HexField(area, allowAlpha).also { widgets.add(it.input) }
    private val chroma = ChromaControls(initialChroma, initialChromaSpeed, initialChromaMode, supportsPerCharMode, onChromaChange)
        .also { if (supportsChroma) widgets.add(it) }

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val contentW = area.contentWidth
        val w = contentW + Theme.PADDING * 2f
        val h = Theme.PADDING + SV_SIZE + (if (allowAlpha) ALPHA_BLOCK else 0f) + ROW_GAP + HEX_H +
            (if (supportsChroma) chroma.blockHeight() else 0f) + Theme.PADDING

        placeUnder(anchorX, anchorY, anchorW, anchorH, w, h, GAP)

        drawCard(rise = RISE) {
            val y = card.y
            val left = card.x + Theme.PADDING
            val right = card.x + w - Theme.PADDING
            area.render(left, y + Theme.PADDING)

            val hexY = area.bottomY + ROW_GAP
            hex.input.render(left, hexY, hex.width, HEX_H, mouseX, mouseY)
            var actionsX = right - HEX_H
            iconButton(actionsX, hexY, LucideIcons.maximize, I18n.trOr("blackaddons.colorpicker.expand", "Open the full picker")) { expand() }
            actionsX -= Theme.PADDING_SMALL
            if (default != null) {
                actionsX -= HEX_H
                iconButton(actionsX, hexY, LucideIcons.rotateCcw, I18n.trOr("blackaddons.colorpicker.reset", "Reset")) { reset() }
                actionsX -= Theme.PADDING_SMALL
            }
            val swatchX = left + hex.width + Theme.PADDING_SMALL
            val swatchW = actionsX - swatchX
            Render.rect(swatchX, hexY, swatchW, HEX_H, area.color, Theme.RADIUS_SMALL)
            Render.hollowRect(swatchX, hexY, swatchW, HEX_H, 1f, Theme.BORDER, Theme.RADIUS_SMALL)

            if (supportsChroma) chroma.render(left, hexY + HEX_H + CHROMA_GAP, contentW, mouseX, mouseY)
        }
    }

    private fun iconButton(x: Float, y: Float, icon: VectorIcon, tooltip: String, onClick: () -> Unit) {
        val hovered = widgets.clickable(x, y, HEX_H, HEX_H, onClick = onClick)
        Render.rect(x, y, HEX_H, HEX_H, if (hovered) Theme.SURFACE_HOVER else Theme.SURFACE, Theme.RADIUS_SMALL)
        Render.icon(icon, centered(x, HEX_H, ICON), centered(y, HEX_H, ICON), ICON, if (hovered) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY)
        if (hovered) Tooltip.show(tooltip)
    }

    private fun reset() {
        val d = default ?: return
        if (hex.input.focused) hex.cancel()
        area.setColor(d)
        if (supportsChroma) chroma.reset(defaultChroma, defaultChromaSpeed, defaultChromaMode)
    }

    private fun expand() {
        if (hex.input.focused) hex.commit()
        UiManager.pop()
        UiManager.push(
            ColorPickerScreen(
                initial = area.color,
                allowAlpha = allowAlpha,
                supportsChroma = supportsChroma,
                supportsPerCharMode = supportsPerCharMode,
                initialChroma = chroma.chroma,
                initialChromaSpeed = chroma.speed,
                initialChromaMode = chroma.mode,
                default = default,
                defaultChroma = defaultChroma,
                defaultChromaSpeed = defaultChromaSpeed,
                defaultChromaMode = defaultChromaMode,
                onChange = onChange,
                onChromaChange = onChromaChange,
            ),
        )
    }

    init {
        keys.on(Keys.ENTER) { UiManager.pop() }
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (hex.input.focused && !hex.input.contains(mouse.x, mouse.y)) hex.commit()
        return super.mouseClicked(mouse)
    }

    override fun onClose() {
        if (hex.input.focused) hex.commit()
    }

    private companion object {
        const val SV_SIZE = 132f
        const val HEX_H = 20f
        const val ICON = 12f

        const val ALPHA_BLOCK = 26f
        const val ROW_GAP = 8f

        /** Already counted in [ChromaControls.blockHeight]. */
        const val CHROMA_GAP = 18f

        const val GAP = 6f
        const val RISE = 4f
    }
}
