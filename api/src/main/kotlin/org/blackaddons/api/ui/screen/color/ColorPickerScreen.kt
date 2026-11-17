package org.blackaddons.api.ui.screen.color
import org.blackaddons.api.feature.settings.impl.ChromaMode
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.I18n
import java.awt.Color

/** Full HSV + alpha + hex picker over whatever opened it. Updates live via [onChange]; Done/Esc closes. */
class ColorPickerScreen(
    initial: Color,
    private val allowAlpha: Boolean,
    private val supportsChroma: Boolean = false,
    supportsPerCharMode: Boolean = false,
    initialChroma: Boolean = false,
    initialChromaSpeed: Float = 0.5f,
    initialChromaMode: ChromaMode = ChromaMode.ALL,
    /** Non-null shows a Reset button. */
    private val default: Color? = null,
    private val defaultChroma: Boolean = false,
    private val defaultChromaSpeed: Float = 0.5f,
    private val defaultChromaMode: ChromaMode = ChromaMode.ALL,
    private val onChange: (Color) -> Unit,
    onChromaChange: (chroma: Boolean, speed: Float, mode: ChromaMode) -> Unit = { _, _, _ -> },
) : ModalPanel(I18n.tr("blackaddons.colorpicker.title")) {

    private val colorArea = widgets.add(ColorArea(initial, allowAlpha, onChange))
    private val chroma = ChromaControls(initialChroma, initialChromaSpeed, initialChromaMode, supportsPerCharMode, onChromaChange)
        .also { if (supportsChroma) widgets.add(it) }

    private val hex = HexField(colorArea, allowAlpha).also { widgets.add(it.input) }

    private var hexX = 0f; private var hexY = 0f
    private val hexH = 22f
    private var doneX = 0f; private var doneY = 0f; private val doneW = 80f; private val doneH = 24f
    private var resetX = 0f; private var resetY = 0f; private val resetW = 70f; private val resetH = 24f

    override val panelWidth: Float get() = 320f
    override val panelHeight: Float
        get() = (if (allowAlpha) 370f else 340f) + if (supportsChroma) chroma.blockHeight() else 0f

    private val doneButton = widgets.add(Button(I18n.tr("blackaddons.colorpicker.done")) { UiManager.pop() })
    private val resetButton = widgets.add(Button(I18n.trOr("blackaddons.colorpicker.reset", "Reset")) { resetToDefault() })

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val px = panelX
        val py = panelY
        val pw = panelW
        val ph = panelH

        val areaX = px + Theme.PADDING_LARGE
        val areaY = py + Theme.PADDING_LARGE + Theme.FONT_TITLE + Theme.PADDING
        colorArea.render(areaX, areaY)

        hexX = areaX
        hexY = colorArea.bottomY + 14f
        hex.input.render(hexX, hexY, hex.width, hexH, mouseX, mouseY)

        val current = colorArea.color
        val rgbLabel = "R ${current.red}  G ${current.green}  B ${current.blue}" +
            if (allowAlpha) "  A ${current.alpha}" else ""
        Render.text(
            rgbLabel,
            hexX + hex.width + 12f,
            Render.centerY(hexY, hexH, TextStyle.SMALL_MUTED),
            TextStyle.SMALL_MUTED,
        )

        if (supportsChroma) chroma.render(areaX, hexY + hexH + 18f, colorArea.contentWidth, mouseX, mouseY)

        doneX = px + pw - doneW - Theme.PADDING_LARGE
        doneY = py + ph - doneH - Theme.PADDING_LARGE
        doneButton.render(doneX, doneY, doneW, doneH, mouseX, mouseY)

        if (default != null) {
            resetX = doneX - resetW - Theme.PADDING
            resetY = centered(doneY, doneH, resetH)
            resetButton.render(resetX, resetY, resetW, resetH, mouseX, mouseY)
        } else {
            resetButton.clear()
        }
    }

    private fun resetToDefault() {
        val d = default ?: return
        if (hex.input.focused) hex.cancel()
        colorArea.setColor(d)
        chroma.reset(defaultChroma, defaultChromaSpeed, defaultChromaMode)
    }

    override fun contentClicked(mouse: MouseButtonInput): Boolean {
        // Clicking away commits the hex field.
        if (hex.input.focused && !hex.input.contains(mouse.x, mouse.y)) hex.commit()
        return super.contentClicked(mouse)
    }

    override fun onClose() {
        if (hex.input.focused) hex.commit()
    }
}
