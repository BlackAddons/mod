package org.blackaddons.api.ui.node

import org.blackaddons.api.input.InputBinding
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.CheckListPopover
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.widget.*
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.roundToDecimals
import java.awt.Color
import kotlin.math.abs
import kotlin.math.roundToInt

internal class NodePropertyEditor(private val canvas: NodeCanvas) {

    private val graph get() = canvas.graph
    private val camera get() = canvas.camera

    private val editor = TextInput().apply { blurOnClickOutside = false }
    private var editNode: GraphNode? = null
    private var editProperty: NodeProperty? = null

    var listening: NodeProperty.Binding? = null
        private set
    private var listeningNode: GraphNode? = null

    private var swatchNode: GraphNode? = null
    private var swatchBefore: List<Pair<NodeProperty, PropertyValue<*>>> = emptyList()

    private var scrubNode: GraphNode? = null
    private var scrubProperty: NodeProperty.Number? = null
    private var scrubBefore: List<Pair<NodeProperty, PropertyValue<*>>> = emptyList()
    private var scrubStart = 0.0
    private var scrubPressX = 0f
    private var scrubArrow = 0
    private var scrubbing = false

    val editedProperty: NodeProperty? get() = editProperty

    init {
        editor.onEnter = { finishTextEdit(commit = true) }
        editor.onEscape = { finishTextEdit(commit = false) }
    }

    /** Non-null when the text field or a waiting binding took the click. */
    fun mouseClicked(mouse: MouseButtonInput): Boolean? {
        finishSwatch()
        if (editNode != null) {
            if (mouse.isLeft && editor.suggestionClicked(mouse.x, mouse.y)) return true
            if (editor.contains(mouse.x, mouse.y)) return editor.mouseClicked(mouse)
            finishTextEdit(commit = true)
        }
        listening?.let { property ->
            val node = listeningNode
            stopListening()
            if (canvas.box.has(mouse.x, mouse.y) && node != null) {
                edit(node) { property.set(InputBinding.mouse(mouse.button)) }
                return true
            }
        }
        return null
    }

    fun mouseDragged(drag: MouseDragInput): Boolean =
        editNode != null && editor.mouseDragged(drag)

    fun mouseReleased(mouse: MouseButtonInput) {
        if (editNode != null) editor.mouseReleased(mouse)
    }

    fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        finishSwatch()
        return editNode != null && editor.suggestionScrolled(wheel)
    }

    fun keyPressed(key: KeyInput): Boolean {
        finishSwatch()
        listening?.let { property ->
            val node = listeningNode
            stopListening()
            val binding = if (key.code == Keys.DELETE || key.code == Keys.BACKSPACE) InputBinding.NONE else InputBinding.keyboard(key.code)
            if (node != null && key.code != Keys.ESCAPE) edit(node) { property.set(binding) }
            return true
        }
        if (editNode != null) {
            editor.keyPressed(key)
            return true
        }
        return false
    }

    fun charTyped(typed: CharInput): Boolean = editNode != null && editor.charTyped(typed)

    /** True when it starts a scrub driven by [scrubDrag]. */
    fun press(node: GraphNode, property: NodeProperty, wx: Float, pressX: Float): Boolean {
        when (property) {
            is NodeProperty.Toggle -> edit(node) { property.set(!property.get()) }
            is NodeProperty.Choice -> UiManager.contextMenu(
                property.options.mapIndexed { i, option ->
                    ContextMenu.Item(option, checked = { i == property.get() }) { edit(node) { property.set(i) } }
                },
            )
            is NodeProperty.Flags -> {
                val box = fieldOnScreen(node, property) ?: return false
                UiManager.push(
                    CheckListPopover(
                        property.options.map { it to property.labelOf(it) },
                        checked = { it in property.get() },
                        onToggle = { id -> edit(node) { property.set(property.get().let { if (id in it) it - id else it + id }) } },
                        box[0], box[1], box[2], box[3],
                    ),
                )
            }
            is NodeProperty.Text -> startTextEdit(node, property, property.get())
            is NodeProperty.Number -> {
                val left = NodeMetrics.fieldX(node, property)
                scrubArrow = when {
                    wx < left + NodeMetrics.ARROW_W -> -1
                    wx >= NodeMetrics.fieldRight(node) - NodeMetrics.ARROW_W -> 1
                    else -> 0
                }
                scrubNode = node
                scrubProperty = property
                scrubBefore = snapshot(node)
                scrubStart = property.get()
                scrubPressX = pressX
                scrubbing = false
                return true
            }
            is NodeProperty.Swatch -> {
                swatchNode = node
                swatchBefore = snapshot(node)
                UiManager.push(
                    ColorPopover(Color(property.get() or OPAQUE, true), allowAlpha = false) { color ->
                        property.set(color.rgb and RGB)
                        graph.styleChanged(node)
                    },
                )
            }
            is NodeProperty.Binding -> {
                listening = property
                listeningNode = node
                graph.styleChanged(node)
            }
            is NodeProperty.Buttons -> property.buttons.getOrNull(NodeMetrics.buttonAt(node, property, wx))?.let { button ->
                edit(node) { button.second() }
            }
            // GraphNode drops notes, so a node never holds one.
            is NodeProperty.Note -> {}
        }
        return false
    }

    fun scrubDrag(mx: Float) {
        if (!scrubbing && abs(mx - scrubPressX) > NodeCanvasInput.CLICK_SLOP) scrubbing = true
        if (scrubbing) scrubTo(mx)
    }

    private fun scrubTo(mx: Float) {
        val node = scrubNode ?: return
        val property = scrubProperty ?: return
        val fine = if (UiKeys.shift) FINE_SCRUB else 1.0
        var value = scrubStart + (mx - scrubPressX) / SCRUB_PX * property.step * fine
        if (UiKeys.ctrl) value = (value / property.step).roundToInt() * property.step
        property.set(clamp(property, value))
        graph.styleChanged(node)
    }

    /** A drag is kept, an arrow click steps, any other click types. */
    fun finishScrub() {
        val node = scrubNode ?: return
        val property = scrubProperty ?: return
        when {
            scrubbing -> recordEdit(node, scrubBefore)
            scrubArrow != 0 -> edit(node) { property.set(clamp(property, property.get() + scrubArrow * property.step)) }
            else -> startTextEdit(node, property, property.get().formatDecimals(property.decimals))
        }
    }

    fun cancelScrub() {
        val node = scrubNode ?: return
        scrubBefore.forEach { (_, value) -> value.restore() }
        graph.styleChanged(node)
    }

    fun endScrub() {
        scrubNode = null
        scrubProperty = null
        scrubBefore = emptyList()
        scrubbing = false
    }

    /** Swatch and text edits are kept, a waiting binding is dropped. */
    fun settle() {
        finishSwatch()
        finishTextEdit(commit = true)
        stopListening()
    }

    private fun clamp(property: NodeProperty.Number, value: Double): Double =
        value.roundToDecimals(property.decimals).coerceIn(property.min, property.max)

    private fun startTextEdit(node: GraphNode, property: NodeProperty, text: String) {
        val textual = property as? NodeProperty.Text
        editNode = node
        editProperty = property
        graph.styleChanged(node)
        editor.placeholder = textual?.placeholder ?: ""
        editor.suggestions = null
        editor.text = text
        editor.suggestions = textual?.suggestions
        editor.suggestionDisplay = textual?.suggestionDisplay
        editor.editor.selectAll()
        editor.focused = true
    }

    private fun finishTextEdit(commit: Boolean) {
        val node = editNode ?: return
        val property = editProperty
        editNode = null
        editProperty = null
        editor.focused = false
        if (node.isAttached) graph.styleChanged(node)
        if (!commit || !node.isAttached) return
        val text = editor.text
        when (property) {
            is NodeProperty.Text -> edit(node) { property.set(text) }
            is NodeProperty.Number -> text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let { value -> edit(node) { property.set(clamp(property, value)) } }
            else -> {}
        }
    }

    fun draw() {
        val node = editNode ?: return
        val property = editProperty ?: return
        if (!node.isAttached) return finishTextEdit(commit = false)
        val box = fieldOnScreen(node, property) ?: return finishTextEdit(commit = false)
        if (!canvas.propertiesShown) return finishTextEdit(commit = true)
        editor.fontSize = NodeMetrics.LABEL_FONT * camera.zoom.toFloat()
        editor.suggestionMinWidth = SUGGESTIONS_MIN_W
        Render.pushScissor(canvas.boxX, canvas.boxY, canvas.boxW, canvas.boxH)
        editor.render(box[0], box[1], box[2], box[3], UiMouse.x, UiMouse.y)
        Render.popScissor()
    }

    fun renderOverlay(mouseX: Float, mouseY: Float) {
        if (editNode != null) editor.renderSuggestions(mouseX, mouseY)
    }

    private fun fieldOnScreen(node: GraphNode, property: NodeProperty): FloatArray? {
        val index = node.properties.indexOfFirst { it === property }
        if (index < 0) return null
        val zoom = camera.zoom.toFloat()
        val left = NodeMetrics.fieldX(node, property)
        return floatArrayOf(
            canvas.boxX + camera.toScreenX(left.toDouble()),
            canvas.boxY + camera.toScreenY(NodeMetrics.fieldY(node, index).toDouble()),
            (NodeMetrics.fieldRight(node) - left) * zoom,
            NodeMetrics.FIELD_H * zoom,
        )
    }

    private fun stopListening() {
        listening = null
        listeningNode?.let(graph::styleChanged)
        listeningNode = null
    }

    /* The popover takes all input while open, so any input here means it closed. */
    private fun finishSwatch() {
        val node = swatchNode ?: return
        swatchNode = null
        if (node.isAttached) recordEdit(node, swatchBefore)
    }

    private fun snapshot(node: GraphNode): List<Pair<NodeProperty, PropertyValue<*>>> =
        node.properties.mapNotNull { property -> property.value()?.let { property to it } }

    private fun edit(node: GraphNode, change: () -> Unit) {
        val before = snapshot(node)
        change()
        recordEdit(node, before)
    }

    private fun recordEdit(node: GraphNode, before: List<Pair<NodeProperty, PropertyValue<*>>>) {
        val changes = before.mapNotNull { (property, old) ->
            val now = property.value() ?: return@mapNotNull null
            if (now != old) PropertyChange(old, now) else null
        }
        if (changes.isEmpty()) return graph.styleChanged(node)
        val command = EditProperties(node, changes, ::propertyEdited)
        propertyEdited(node)
        val pushed = canvas.pusher.pushOut(setOf(node.id), pushers = listOf(node.id))
        canvas.history.record(if (pushed.isEmpty()) command else CompoundCommand(listOf(command) + pushed))
    }

    private fun propertyEdited(node: GraphNode) {
        if (!node.isAttached) return
        canvas.onPropertyEdited(node)
        graph.geometryChanged(node)
    }

    private companion object {
        const val SCRUB_PX = 4f
        const val FINE_SCRUB = 0.1
        const val SUGGESTIONS_MIN_W = 220f
        const val OPAQUE = 0xFF000000.toInt()
        const val RGB = 0xFFFFFF
    }
}
