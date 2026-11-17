package org.blackaddons.api.ui.node

import org.blackaddons.api.input.InputBinding
import org.blackaddons.api.ui.widget.MovementKeybindSelector

/** Blender-style editable row in a node body. Reads/writes the host value through accessors every time; edits undo. */
sealed class NodeProperty(val label: String) {

    /** Drag to scrub (Shift fine, Ctrl snaps to [step]), click to type, arrows step. */
    class Number(
        label: String,
        val get: () -> Double,
        val set: (Double) -> Unit,
        val step: Double = 1.0,
        val min: Double = Double.NEGATIVE_INFINITY,
        val max: Double = Double.POSITIVE_INFINITY,
        val decimals: Int = 2,
        /** Appended to the label from the current value, e.g. a unit conversion. */
        val labelSuffix: ((Double) -> String)? = null,
    ) : NodeProperty(label)

    class Toggle(label: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : NodeProperty(label)
    class Choice(label: String, val options: List<String>, val get: () -> Int, val set: (Int) -> Unit) : NodeProperty(label)

    /** Multi-select menu over [options] (shown via [labelOf]); [none] shows when empty. */
    open class Flags(
        label: String,
        val options: List<String>,
        val get: () -> Set<String>,
        val set: (Set<String>) -> Unit,
        val labelOf: (String) -> String = { it },
        val none: String = "",
    ) : NodeProperty(label)

    /**
     * Movement keys by [MovementKeybindSelector] chip id, in the selector's order. A form shows the chips and a node
     * shows the menu.
     */
    class MovementKeys(label: String, val keys: () -> List<String>, val setKeys: (List<String>) -> Unit) : Flags(
        label,
        MovementKeybindSelector.CHIPS.map { it.id },
        { keys().toSet() },
        { chosen -> setKeys(MovementKeybindSelector.CHIPS.map { it.id }.filter { it in chosen }) },
        labelOf = { id -> MovementKeybindSelector.CHIPS.firstOrNull { it.id == id }?.let { "${it.label} (${it.detail})" } ?: id },
    )

    /** Enter or click-away keeps, Escape drops. */
    class Text(
        label: String,
        val get: () -> String,
        val set: (String) -> Unit,
        val placeholder: String = "",
        val suggestions: ((String) -> List<String>)? = null,
        val suggestionDisplay: ((String) -> String)? = null,
    ) : NodeProperty(label)

    /** RGB, picked in a popover. */
    class Swatch(label: String, val get: () -> Int, val set: (Int) -> Unit) : NodeProperty(label)

    /** Click, then press a key/button; Delete clears, Escape cancels. */
    class Binding(
        label: String,
        val get: () -> InputBinding,
        val set: (InputBinding) -> Unit,
        val describe: (InputBinding) -> String,
        val prompt: String,
    ) : NodeProperty(label)

    /** Changes they make to other rows undo as one edit. */
    class Buttons(val buttons: List<Pair<String, () -> Unit>>) : NodeProperty("")

    /** Help text. Forms show it and node bodies skip it. */
    class Note(val text: String) : NodeProperty("")

    internal val labelBeside: Boolean
        get() = label.isNotEmpty() && this !is Number && this !is Toggle

    internal fun value(): PropertyValue<*>? = when (this) {
        is Number -> PropertyValue(get(), set)
        is Toggle -> PropertyValue(get(), set)
        is Choice -> PropertyValue(get(), set)
        is Flags -> PropertyValue(get().toSet(), set)
        is Text -> PropertyValue(get(), set)
        is Swatch -> PropertyValue(get(), set)
        is Binding -> PropertyValue(get(), set)
        is Buttons, is Note -> null
    }
}

/** Keeps its own setter so restoring needs no cast. */
internal class PropertyValue<T>(private val value: T, private val set: (T) -> Unit) {
    fun restore() = set(value)

    override fun equals(other: Any?): Boolean = other is PropertyValue<*> && other.value == value
    override fun hashCode(): Int = value.hashCode()
}

internal class PropertyChange(val before: PropertyValue<*>, val after: PropertyValue<*>)

/** [refresh] tells the canvas on undo/redo so the host can rebuild its rows. */
class EditProperties internal constructor(
    val node: GraphNode,
    private val changes: List<PropertyChange>,
    private val refresh: (GraphNode) -> Unit,
) : GraphCommand {
    override val structural: Boolean get() = false

    override fun apply(graph: NodeGraph) {
        changes.forEach { it.after.restore() }
        refresh(node)
    }

    /** Forward order on purpose: a setter that adjusts later rows must run first. */
    override fun revert(graph: NodeGraph) {
        changes.forEach { it.before.restore() }
        refresh(node)
    }
}
