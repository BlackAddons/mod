package org.blackaddons.platform.impl.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.FrameClock
import org.blackaddons.api.util.color.lerpColor
import java.util.IdentityHashMap
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * A small "are you sure" card next to a vanilla button: the question over a row of choices. The choices are real vanilla
 * buttons, so the keyboard works. The owner keeps vanilla from drawing them, calls [layout] every frame and [draw] from
 * its screen render.
 */
internal class ConfirmCard {

    class Choice(val widget: AbstractWidget, val label: String, val danger: Boolean = false)

    private class Box(val x: Float, val y: Float, val width: Float, val height: Float)

    private var box: Box? = null
    private var question = ""
    private var choices = emptyList<Choice>()
    private var scale = 1f
    private val hover = IdentityHashMap<AbstractWidget, Float>()
    private val frameClock = FrameClock(cap = 100.milliseconds)

    val shown: Boolean get() = box != null

    private fun sf(value: Float): Float = value * scale
    private fun si(value: Int): Int = (value * scale).roundToInt()

    /** Places the card at [anchor] in game units, [scale] per menu unit. The choices stay hidden while it is closed. */
    fun layout(open: Boolean, anchor: AbstractWidget?, question: String, choices: List<Choice>, screenWidth: Int, screenHeight: Int, scale: Float) {
        this.question = question
        this.choices = choices
        this.scale = scale
        choices.forEach { it.widget.visible = open && anchor != null }
        if (!open || anchor == null || choices.isEmpty()) {
            box = null
            return
        }
        val buttonWidths = choices.map { Render.textWidth(it.label, sf(TEXT), Fonts.medium).toInt() + si(BUTTON_PAD) * 2 }
        val rowWidth = buttonWidths.sum() + si(GAP) * (choices.size - 1)
        val textHeight = Render.textHeight(sf(TEXT), Fonts.medium)
        val width = maxOf(rowWidth.toFloat(), Render.textWidth(question, sf(TEXT), Fonts.medium)) + si(PAD) * 2
        val height = si(PAD) + textHeight + si(GAP) + si(BUTTON_HEIGHT) + si(PAD)
        val x = (anchor.x + anchor.width / 2f - width / 2).coerceIn(si(EDGE).toFloat(), screenWidth - width - si(EDGE))
        val below = anchor.y + anchor.height + si(GAP).toFloat()
        val y = if (below + height <= screenHeight - si(EDGE)) below else anchor.y - height - si(GAP)
        box = Box(x, y, width, height)

        var buttonX = (x + (width - rowWidth) / 2).toInt()
        val buttonY = (y + height - si(PAD) - si(BUTTON_HEIGHT)).toInt()
        choices.forEachIndexed { i, choice ->
            choice.widget.setSize(buttonWidths[i], si(BUTTON_HEIGHT))
            choice.widget.setPosition(buttonX, buttonY)
            buttonX += buttonWidths[i] + si(GAP)
        }
    }

    fun draw() {
        val box = box ?: return
        val dt = frameClock.tick()
        Render.rect(box.x, box.y, box.width, box.height, Theme.SURFACE.withAlpha(240), radius = sf(Theme.RADIUS_SMALL))
        Render.hollowRect(box.x, box.y, box.width, box.height, 1f, Theme.BORDER, radius = sf(Theme.RADIUS_SMALL))
        Render.textCentered(question, box.x + box.width / 2, box.y + si(PAD), sf(TEXT), Theme.TEXT_PRIMARY, Fonts.medium)
        choices.forEach { choice ->
            val w = choice.widget
            val target = if (w.active && highlighted(w)) 1f else 0f
            val current = hover[w] ?: 0f
            val lit = current + (target - current) * (1 - exp(-dt * HOVER_RATE)).toFloat()
            hover[w] = lit
            val fill = lerpColor(Theme.SURFACE_HOVER, if (choice.danger) Theme.DANGER.withAlpha(200) else Theme.SURFACE_ACTIVE, lit)
            Render.rect(w.x, w.y, w.width, w.height, fill, radius = sf(Theme.RADIUS_SMALL))
            val color = if (choice.danger) lerpColor(Theme.DANGER, Theme.TEXT_PRIMARY, lit) else Theme.TEXT_PRIMARY
            Render.textCentered(choice.label, w.x + w.width / 2f, Render.centerY(w.y, w.height, sf(TEXT), Fonts.medium), sf(TEXT), color, Fonts.medium)
        }
        hover.keys.retainAll(choices.map { it.widget }.toSet())
    }

    private companion object {
        const val PAD = 6
        const val GAP = 6
        const val TEXT = 9f
        const val BUTTON_HEIGHT = 14
        const val BUTTON_PAD = 8
        const val EDGE = 6
        const val HOVER_RATE = 14.0
    }
}

/**
 * Focus highlights a widget only during keyboard navigation. A clicked button keeps focus, and a click that opens the
 * browser (Website) never moves it, so the highlight used to just stay on.
 */
internal fun highlighted(widget: AbstractWidget): Boolean =
    widget.isHovered || (widget.isFocused && Minecraft.getInstance().lastInputType.isKeyboard)
