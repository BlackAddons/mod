package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.widget.Chevron
import org.blackaddons.api.util.formatDecimals
import java.awt.Color
import kotlin.math.max

internal class NodeBodyPainter(private val canvas: NodeCanvas, private val style: NodeCanvasStyle) {

    private val graph get() = canvas.graph

    private class NodeText {
        var revision = -1
        var width = -1f
        var title = ""
        var subtitle = ""
        var inputs: Array<String> = emptyArray()
        var outputs: Array<String> = emptyArray()
        var outputWidths = FloatArray(0)
    }

    private var texts = arrayOfNulls<NodeText>(64)

    private val titleHeight by lazy { Render.textHeight(NodeMetrics.TITLE_FONT, Fonts.medium) }
    val labelHeight by lazy { Render.textHeight(NodeMetrics.LABEL_FONT, Fonts.regular) }
    private val subtitleHeight by lazy { Render.textHeight(NodeMetrics.SUBTITLE_FONT, Fonts.regular) }

    fun forget(id: Int) {
        if (id < texts.size) texts[id] = null
    }

    fun clear() = texts.fill(null)

    private fun textOf(node: GraphNode): NodeText {
        if (node.id >= texts.size) texts = texts.copyOf(max(node.id + 1, texts.size * 2))
        val cached = texts[node.id]
        if (cached != null && cached.revision == node.revision && cached.width == node.width) return cached
        val text = cached ?: NodeText().also { texts[node.id] = it }
        val pad = NodeMetrics.PAD
        val badge = (if (node.warning != null) BADGE_ROOM else 0f) + toggleRoom(node)
        text.title = Render.ellipsize(node.title, node.width - pad * 2f - badge, NodeMetrics.TITLE_FONT, Fonts.medium)
        text.subtitle = Render.ellipsize(node.subtitle, node.width - pad * 2f, NodeMetrics.SUBTITLE_FONT, Fonts.regular)
        val half = node.width / 2f - pad - LABEL_INSET
        text.inputs = Array(node.inputs.size) { Render.ellipsize(node.inputs[it].label, half, NodeMetrics.LABEL_FONT, Fonts.regular) }
        text.outputs = Array(node.outputs.size) { Render.ellipsize(node.outputs[it].label, half, NodeMetrics.LABEL_FONT, Fonts.regular) }
        text.outputWidths = FloatArray(node.outputs.size) { Render.textWidth(text.outputs[it], NodeMetrics.LABEL_FONT, Fonts.regular) }
        text.revision = node.revision
        text.width = node.width
        return text
    }

    fun drawNode(node: GraphNode, dx: Float, dy: Float, showText: Boolean, full: Boolean) {
        val x = node.x + dx
        val y = node.y + dy
        val w = node.width
        val h = node.height
        val r = NodeMetrics.RADIUS
        Render.rect(x, y, w, h, if (node.dimmed) style.bodyDim else style.body, r)
        Render.halfRoundedRect(x, y, w, NodeMetrics.HEADER, style.headerOf(node), r, roundTop = true)

        val text = if (showText) textOf(node) else null
        if (text != null) {
            Render.text(
                text.title, x + NodeMetrics.PAD, centered(y, NodeMetrics.HEADER, titleHeight),
                NodeMetrics.TITLE_FONT, if (node.dimmed) style.textMuted else style.textPrimary, Fonts.medium,
            )
        }
        if (node.toggleable) {
            Render.toggleSwitch(
                NodeMetrics.toggleX(node) + dx, NodeMetrics.toggleY(node) + dy,
                NodeMetrics.TOGGLE_W, NodeMetrics.TOGGLE_H, if (node.dimmed) 0f else 1f, TOGGLE_ON, TOGGLE_OFF,
            )
        }
        if (node.warning != null) {
            Render.circle(x + w - NodeMetrics.PAD - toggleRoom(node) - WARNING_RADIUS, y + NodeMetrics.HEADER / 2f, WARNING_RADIUS, style.danger)
        }

        val socketScale = if (full) 1f else SMALL_SOCKET
        for (slot in node.inputs.indices) {
            val sy = NodeMetrics.socketY(node, slot) + dy
            drawSocket(node, slot, output = false, x, sy, socketScale, hovered = false)
            if (full && text != null) {
                Render.text(
                    text.inputs[slot], x + NodeMetrics.PAD + LABEL_INSET, sy - labelHeight / 2f,
                    NodeMetrics.LABEL_FONT, style.textMuted, Fonts.regular,
                )
            }
        }
        for (slot in node.outputs.indices) {
            val sy = NodeMetrics.socketY(node, slot) + dy
            drawSocket(node, slot, output = true, x + w, sy, socketScale, hovered = false)
            if (full && text != null) {
                Render.text(
                    text.outputs[slot], x + w - NodeMetrics.PAD - LABEL_INSET - text.outputWidths[slot], sy - labelHeight / 2f,
                    NodeMetrics.LABEL_FONT, style.textMuted, Fonts.regular,
                )
            }
        }
        if (full && text != null && node.subtitle.isNotEmpty()) {
            val rows = max(node.inputs.size, node.outputs.size)
            val top = y + NodeMetrics.HEADER + NodeMetrics.PAD + rows * NodeMetrics.ROW
            Render.text(
                text.subtitle, x + NodeMetrics.PAD, centered(top, NodeMetrics.SUBTITLE, subtitleHeight),
                NodeMetrics.SUBTITLE_FONT, style.textMuted, Fonts.regular,
            )
        }
        if (full && text != null) node.properties.forEachIndexed { i, property -> drawProperty(node, i, property, dx, dy) }
    }

    /* Read as drawn; the canvas invalidates the cached scene on every edit. */
    private fun drawProperty(node: GraphNode, index: Int, property: NodeProperty, dx: Float, dy: Float) {
        val font = NodeMetrics.LABEL_FONT
        val left = NodeMetrics.fieldX(node, property) + dx
        val right = NodeMetrics.fieldRight(node) + dx
        val top = NodeMetrics.fieldY(node, index) + dy
        val w = right - left
        val h = NodeMetrics.FIELD_H
        val ty = centered(top, h, labelHeight)
        if (property.labelBeside) {
            val labelX = node.x + dx + NodeMetrics.PAD
            Render.text(Render.ellipsize(property.label, left - labelX - FIELD_GAP, font, Fonts.regular), labelX, ty, font, style.textMuted, Fonts.regular)
        }
        if (property === canvas.properties.editedProperty) return
        when (property) {
            is NodeProperty.Number -> {
                Render.rect(left, top, w, h, style.fieldFill, FIELD_RADIUS)
                val value = property.get().formatDecimals(property.decimals)
                val valueW = Render.textWidth(value, font, Fonts.regular)
                Render.text(value, right - NodeMetrics.ARROW_W - valueW, ty, font, style.textPrimary, Fonts.regular)
                val room = w - NodeMetrics.ARROW_W * 2f - valueW - FIELD_GAP
                val label = property.label + (property.labelSuffix?.invoke(property.get()) ?: "")
                Render.text(Render.ellipsize(label, room, font, Fonts.regular), left + NodeMetrics.ARROW_W, ty, font, style.textMuted, Fonts.regular)
                drawCentered("<", left, NodeMetrics.ARROW_W, ty, style.textMuted)
                drawCentered(">", right - NodeMetrics.ARROW_W, NodeMetrics.ARROW_W, ty, style.textMuted)
            }
            is NodeProperty.Toggle -> {
                val box = h - CHECK_INSET * 2f
                val boxX = left + FIELD_INSET
                if (property.get()) Render.rect(boxX, top + CHECK_INSET, box, box, style.accent, CHECK_RADIUS)
                else Render.hollowRect(boxX, top + CHECK_INSET, box, box, 1f, style.border, CHECK_RADIUS)
                val labelX = boxX + box + FIELD_GAP
                Render.text(Render.ellipsize(property.label, right - FIELD_INSET - labelX, font, Fonts.regular), labelX, ty, font, style.textPrimary, Fonts.regular)
            }
            is NodeProperty.Swatch -> {
                Render.rect(left, top, w, h, Color(property.get() or OPAQUE, true), FIELD_RADIUS)
                Render.hollowRect(left, top, w, h, 1f, style.border, FIELD_RADIUS)
            }
            is NodeProperty.Buttons -> {
                val each = NodeMetrics.buttonWidth(node, property)
                property.buttons.forEachIndexed { i, (label, _) ->
                    val bx = left + i * (each + NodeMetrics.BUTTON_GAP)
                    Render.rect(bx, top, each, h, style.fieldFill, FIELD_RADIUS)
                    Render.hollowRect(bx, top, each, h, 1f, style.border, FIELD_RADIUS)
                    drawCentered(Render.ellipsize(label, each - FIELD_INSET, font, Fonts.regular), bx, each, ty, style.textPrimary)
                }
            }
            else -> {
                Render.rect(left, top, w, h, style.fieldFill, FIELD_RADIUS)
                val (shown, color) = valueOf(property)
                val dropdown = property is NodeProperty.Choice || property is NodeProperty.Flags
                val room = w - FIELD_INSET * 2f - if (dropdown) CHEVRON_ROOM else 0f
                Render.text(Render.ellipsize(shown, room, font, Fonts.regular), left + FIELD_INSET, ty, font, color, Fonts.regular)
                if (dropdown) Chevron.draw(right - CHEVRON_ROOM / 2f, top + h / 2f, CHEVRON_SIZE, CHEVRON_STROKE, 1f)
            }
        }
    }

    private fun valueOf(property: NodeProperty): Pair<String, Color> = when (property) {
        is NodeProperty.Choice -> (property.options.getOrNull(property.get()) ?: "") to style.textPrimary
        is NodeProperty.Text -> property.get().let { if (it.isEmpty()) property.placeholder to style.textMuted else it to style.textPrimary }
        is NodeProperty.Flags -> {
            val chosen = property.get()
            val shown = property.options.filter { it in chosen }.joinToString(", ", transform = property.labelOf)
            if (shown.isEmpty()) property.none to style.textMuted else shown to style.textPrimary
        }
        is NodeProperty.Binding ->
            if (canvas.properties.listening === property) property.prompt to style.accent else property.describe(property.get()) to style.textPrimary
        else -> "" to style.textPrimary
    }

    private fun drawCentered(text: String, x: Float, w: Float, y: Float, color: Color) {
        val font = NodeMetrics.LABEL_FONT
        Render.text(text, x + (w - Render.textWidth(text, font, Fonts.regular)) / 2f, y, font, color, Fonts.regular)
    }

    private fun toggleRoom(node: GraphNode): Float = if (node.toggleable) NodeMetrics.TOGGLE_W + NodeMetrics.PAD else 0f

    fun drawHoveredField() {
        val node = graph.node(canvas.input.hoverNode) ?: return
        val index = canvas.input.hoverProperty
        val property = node.properties.getOrNull(index) ?: return
        if (property === canvas.properties.editedProperty) return
        val top = NodeMetrics.fieldY(node, index)
        val left = NodeMetrics.fieldX(node, property)
        if (property is NodeProperty.Buttons) {
            val button = NodeMetrics.buttonAt(node, property, canvas.input.hoverWorldX)
            if (button < 0) return
            val each = NodeMetrics.buttonWidth(node, property)
            Render.rect(left + button * (each + NodeMetrics.BUTTON_GAP), top, each, NodeMetrics.FIELD_H, style.fieldHover, FIELD_RADIUS)
            return
        }
        Render.rect(left, top, NodeMetrics.fieldRight(node) - left, NodeMetrics.FIELD_H, style.fieldHover, FIELD_RADIUS)
    }

    /**
     * Shows the whole hovered title or row as a tooltip when the node had to cut it off. Measures only what is hovered.
     */
    fun showCutOffText() {
        val node = graph.node(canvas.input.hoverNode) ?: return
        val property = node.properties.getOrNull(canvas.input.hoverProperty)
        val full = when {
            property != null -> if (property === canvas.properties.editedProperty) null else cutOff(node, property)
            canvas.input.hoverWorldY - node.y < NodeMetrics.HEADER -> node.title.takeUnless {
                fits(it, node.width - NodeMetrics.PAD * 2f - (if (node.warning != null) BADGE_ROOM else 0f) - toggleRoom(node), NodeMetrics.TITLE_FONT, Fonts.medium)
            }
            else -> null
        }
        full?.let(Tooltip::show)
    }

    // Must match the space drawProperty gives each text.
    private fun cutOff(node: GraphNode, property: NodeProperty): String? {
        val font = NodeMetrics.LABEL_FONT
        val left = NodeMetrics.fieldX(node, property)
        val w = NodeMetrics.fieldRight(node) - left
        val labelCut = property.labelBeside && !fits(property.label, left - node.x - NodeMetrics.PAD - FIELD_GAP, font, Fonts.regular)
        return when (property) {
            is NodeProperty.Number -> {
                val value = property.get().formatDecimals(property.decimals)
                val label = property.label + (property.labelSuffix?.invoke(property.get()) ?: "")
                val room = w - NodeMetrics.ARROW_W * 2f - Render.textWidth(value, font, Fonts.regular) - FIELD_GAP
                "$label: $value".takeUnless { fits(label, room, font, Fonts.regular) }
            }
            is NodeProperty.Toggle -> property.label.takeUnless { fits(it, w - FIELD_INSET * 2f - (NodeMetrics.FIELD_H - CHECK_INSET * 2f) - FIELD_GAP, font, Fonts.regular) }
            is NodeProperty.Swatch, is NodeProperty.Buttons, is NodeProperty.Note -> null
            else -> {
                val shown = valueOf(property).first
                val room = w - FIELD_INSET * 2f - if (property is NodeProperty.Choice || property is NodeProperty.Flags) CHEVRON_ROOM else 0f
                if (labelCut || !fits(shown, room, font, Fonts.regular)) listOf(property.label, shown).filter { it.isNotEmpty() }.joinToString(": ") else null
            }
        }
    }

    private fun fits(text: String, room: Float, size: Float, font: Font): Boolean = Render.textWidth(text, size, font) <= room

    fun drawHoveredSocket(detail: NodeDetail) {
        val hover = canvas.input.hoverSocket ?: return
        val node = graph.node(hover.node) ?: return
        if (canvas.drag.isMoving(node.id)) return
        val x = if (hover.output) NodeMetrics.outputX(node) else NodeMetrics.inputX(node)
        drawSocket(node, hover.slot, hover.output, x, NodeMetrics.socketY(node, hover.slot), if (detail == NodeDetail.FULL) 1f else SMALL_SOCKET, hovered = true)
    }

    fun drawSockets(node: GraphNode, dx: Float, dy: Float, scale: Float) {
        for (slot in node.inputs.indices) drawSocketAt(node, slot, output = false, dx, dy, scale)
        for (slot in node.outputs.indices) drawSocketAt(node, slot, output = true, dx, dy, scale)
    }

    fun drawSocketAt(node: GraphNode, slot: Int, output: Boolean, dx: Float, dy: Float, scale: Float) {
        val x = if (output) NodeMetrics.outputX(node) else NodeMetrics.inputX(node)
        drawSocket(node, slot, output, x + dx, NodeMetrics.socketY(node, slot) + dy, scale, hovered = false)
    }

    private fun drawSocket(node: GraphNode, slot: Int, output: Boolean, cx: Float, cy: Float, scale: Float, hovered: Boolean) {
        val spec = (if (output) node.outputs else node.inputs).getOrNull(slot) ?: return
        val radius = NodeMetrics.SOCKET_RADIUS * scale * (if (hovered) 1.3f else 1f)
        if (scale < 1f && !hovered) {
            Render.circle(cx, cy, radius, spec.type.color)
            return
        }
        Render.circle(cx, cy, radius + 1.2f, style.background)
        Render.circle(cx, cy, radius, spec.type.color)
        if (!isConnected(node, slot, output)) Render.circle(cx, cy, radius * 0.45f, style.background)
    }

    private fun isConnected(node: GraphNode, slot: Int, output: Boolean): Boolean {
        for (i in 0 until node.links.size) {
            val link = graph.link(node.links[i]) ?: continue
            if (output && link.from == node.id && link.fromSlot == slot) return true
            if (!output && link.to == node.id && link.toSlot == slot) return true
        }
        return false
    }

    companion object {
        const val SMALL_SOCKET = 0.8f
        private const val WARNING_RADIUS = 3f
        private const val BADGE_ROOM = 10f
        private const val LABEL_INSET = 5f

        /* Translucent so it takes the header's tint. */
        val TOGGLE_ON = Color(255, 255, 255, 90)
        val TOGGLE_OFF = Color(0, 0, 0, 80)

        private const val FIELD_RADIUS = 3f
        private const val FIELD_GAP = 4f
        private const val FIELD_INSET = 4f
        private const val CHECK_INSET = 2f
        private const val CHECK_RADIUS = 2f
        private const val CHEVRON_ROOM = 10f
        private const val CHEVRON_SIZE = 3f
        private const val CHEVRON_STROKE = 1.1f
        private const val OPAQUE = 0xFF000000.toInt()
    }
}
