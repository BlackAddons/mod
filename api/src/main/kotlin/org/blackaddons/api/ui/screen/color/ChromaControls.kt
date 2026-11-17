package org.blackaddons.api.ui.screen.color
import org.blackaddons.api.feature.settings.impl.ChromaMode
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.DecayAnimation
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.lerpColor
import java.util.Locale

private val tr = I18n.scope("blackaddons.colorpicker")

internal class ChromaControls(
    chroma: Boolean,
    speed: Float,
    mode: ChromaMode,
    private val supportsPerCharMode: Boolean,
    private val onChange: (chroma: Boolean, speed: Float, mode: ChromaMode) -> Unit,
) : InputTarget {
    var chroma = chroma
        private set
    var speed = speed
        private set
    var mode = mode
        private set

    // Seeded from the opening state so a new picker doesn't animate in from the last one.
    private val toggleAnim = Animation(if (chroma) 1f else 0f)
    private val modeAllAnim = Animation(if (mode == ChromaMode.ALL) 1f else 0f)
    private val modeByAnim = Animation(if (mode == ChromaMode.PER_CHAR) 1f else 0f)
    private val speedFill = DecayAnimation()

    private val speedMin = 0.05f
    private val speedMax = 2.0f

    private var toggleX = 0f; private var toggleY = 0f
    private val toggleW = 30f; private val toggleH = 16f
    private var speedX = 0f; private var speedY = 0f; private var speedW = 0f; private val speedH = 6f
    private var modeAllX = 0f; private var modeByX = 0f; private var modeY = 0f
    private var modePillW = 0f; private val modeH = 18f
    private var draggingSpeed = false

    // Full block for a text color with chroma on, else compact.
    private val expandedH = 104f
    private val collapsedH = 64f

    /** The picker adds it to its own height. */
    fun blockHeight(): Float = if (chroma && supportsPerCharMode) expandedH else collapsedH

    /** From the picker's Reset button. */
    fun reset(chroma: Boolean, speed: Float, mode: ChromaMode) {
        this.chroma = chroma
        this.speed = speed
        this.mode = mode
        onChange(chroma, speed, mode)
    }

    fun render(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        toggleX = x
        toggleY = y
        toggleAnim.animateTo(if (chroma) 1f else 0f)
        Render.toggleSwitch(toggleX, toggleY, toggleW, toggleH, toggleAnim.get())

        Render.text(
            tr("chroma", "Chroma"),
            toggleX + toggleW + 8f,
            Render.centerY(toggleY, toggleH, TextStyle.BODY_PRIMARY_MEDIUM),
            TextStyle.BODY_PRIMARY_MEDIUM,
        )

        speedX = x
        speedY = y + toggleH + 18f
        speedW = w
        val labelColor = if (chroma) Theme.TEXT_SECONDARY else Theme.TEXT_MUTED
        Render.text(
            tr("speed", "Speed"),
            speedX,
            speedY - Theme.FONT_SMALL - 4f,
            Theme.FONT_SMALL, labelColor, Fonts.regular,
        )
        val valueText = "%.2f×".format(Locale.ROOT, speed)
        val vtw = Render.textWidth(valueText, Theme.FONT_SMALL, Fonts.regular)
        Render.text(
            valueText,
            speedX + speedW - vtw,
            speedY - Theme.FONT_SMALL - 4f,
            Theme.FONT_SMALL, labelColor, Fonts.regular,
        )

        val targetT = if (draggingSpeed && speedW > 0f) {
            ((mouseX - speedX) / speedW).coerceIn(0f, 1f)
        } else {
            speedToFraction(speed)
        }
        val frac = speedFill.get(targetT)
        Render.slider(speedX, speedY, speedW, speedH, frac, active = chroma, trackColor = Theme.SURFACE_ACTIVE)

        if (chroma && supportsPerCharMode) {
            val gap = 8f
            modeY = speedY + speedH + 18f
            modePillW = (w - gap) / 2f
            modeAllX = x
            modeByX = x + modePillW + gap
            modeAllAnim.animateTo(if (mode == ChromaMode.ALL) 1f else 0f)
            modeByAnim.animateTo(if (mode == ChromaMode.PER_CHAR) 1f else 0f)
            drawModePill(modeAllX, modeY, modePillW, tr("chroma_all", "All"), modeAllAnim.get(), mouseX, mouseY)
            drawModePill(modeByX, modeY, modePillW, tr("chroma_by_char", "By Char"), modeByAnim.get(), mouseX, mouseY)
        }
    }

    private fun drawModePill(x: Float, y: Float, w: Float, label: String, t: Float, mouseX: Float, mouseY: Float) {
        val hover = hit(mouseX, mouseY, x, y, w, modeH)
        val baseBg = if (hover) Theme.SURFACE_HOVER else Theme.SURFACE.withAlpha(80)
        val bg = lerpColor(baseBg, Theme.ACCENT, t)
        Render.rect(x, y, w, modeH, bg, Theme.RADIUS_SMALL)
        val tw = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
        val textColor = lerpColor(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, t)
        Render.text(
            label, centered(x, w, tw), Render.centerY(y, modeH, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL, textColor, Fonts.medium,
        )
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft) return false
        val mx = mouse.x
        val my = mouse.y
        when {
            hit(mx, my, toggleX, toggleY, toggleW, toggleH) -> {
                chroma = !chroma
                onChange(chroma, speed, mode)
                return true
            }
            hit(mx, my, speedX, speedY - 5f, speedW, speedH + 14f) -> {
                draggingSpeed = true
                updateSpeed(mx)
                return true
            }
            supportsPerCharMode && chroma && hit(mx, my, modeAllX, modeY, modePillW, modeH) -> {
                mode = ChromaMode.ALL
                onChange(chroma, speed, mode)
                return true
            }
            supportsPerCharMode && chroma && hit(mx, my, modeByX, modeY, modePillW, modeH) -> {
                mode = ChromaMode.PER_CHAR
                onChange(chroma, speed, mode)
                return true
            }
        }
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        if (draggingSpeed) {
            updateSpeed(drag.x)
            return true
        }
        return false
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        draggingSpeed = false
        return false
    }

    private fun updateSpeed(mx: Float) {
        speed = fractionToSpeed((mx - speedX) / speedW)
        onChange(chroma, speed, mode)
    }

    private fun speedToFraction(speed: Float): Float =
        ((speed - speedMin) / (speedMax - speedMin)).coerceIn(0f, 1f)

    private fun fractionToSpeed(frac: Float): Float =
        speedMin + frac.coerceIn(0f, 1f) * (speedMax - speedMin)
}
