package org.blackaddons.api.ui.node

import org.blackaddons.api.Platform
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.roundToDecimals
import org.blackaddons.api.world.ViewAngles
import kotlin.math.floor
import kotlin.reflect.KMutableProperty0

/** Builds editor rows: `nodeProperties { number(tr("speed", "Speed"), params::speed, step = 0.5) }`. */
fun nodeProperties(block: NodeRows.() -> Unit): List<NodeProperty> = NodeRows().apply(block).rows

class NodeRows internal constructor() {
    internal val rows = mutableListOf<NodeProperty>()

    fun add(property: NodeProperty) {
        rows += property
    }

    fun number(
        label: String, get: () -> Double, set: (Double) -> Unit, step: Double = 1.0,
        min: Double = Double.NEGATIVE_INFINITY, max: Double = Double.POSITIVE_INFINITY, decimals: Int = 2,
    ) = add(NodeProperty.Number(label, get, set, step, min, max, decimals))

    fun number(
        label: String, property: KMutableProperty0<Double>, step: Double = 1.0,
        min: Double = Double.NEGATIVE_INFINITY, max: Double = Double.POSITIVE_INFINITY, decimals: Int = 2,
    ) = number(label, property::get, property::set, step, min, max, decimals)

    @JvmName("numberFloat")
    fun number(
        label: String, property: KMutableProperty0<Float>, step: Double = 1.0,
        min: Double = Double.NEGATIVE_INFINITY, max: Double = Double.POSITIVE_INFINITY, decimals: Int = 2,
    ) = number(label, { property.get().toDouble() }, { property.set(it.toFloat()) }, step, min, max, decimals)

    @JvmName("numberInt")
    fun number(label: String, property: KMutableProperty0<Int>, min: Double = Double.NEGATIVE_INFINITY, max: Double = Double.POSITIVE_INFINITY) =
        number(label, { property.get().toDouble() }, { property.set(it.toInt()) }, 1.0, min, max, 0)

    fun toggle(label: String, get: () -> Boolean, set: (Boolean) -> Unit) = add(NodeProperty.Toggle(label, get, set))

    fun toggle(label: String, property: KMutableProperty0<Boolean>) = toggle(label, property::get, property::set)

    fun choice(label: String, options: List<String>, get: () -> Int, set: (Int) -> Unit) =
        add(NodeProperty.Choice(label, options, get, set))

    /** One option per entry of [entries], labeled by [labelOf]. */
    fun <T> choice(label: String, entries: List<T>, labelOf: (T) -> String, property: KMutableProperty0<T>) =
        choice(label, entries.map(labelOf), { entries.indexOf(property.get()).coerceAtLeast(0) }, { property.set(entries[it]) })

    fun text(
        label: String, get: () -> String, set: (String) -> Unit, placeholder: String = "",
        suggestions: ((String) -> List<String>)? = null, suggestionDisplay: ((String) -> String)? = null,
    ) = add(NodeProperty.Text(label, get, set, placeholder, suggestions, suggestionDisplay))

    /** Trims what the player typed. */
    fun text(
        label: String, property: KMutableProperty0<String>, placeholder: String = "",
        suggestions: ((String) -> List<String>)? = null, suggestionDisplay: ((String) -> String)? = null,
    ) = text(label, property::get, { property.set(it.trim()) }, placeholder, suggestions, suggestionDisplay)

    fun swatch(label: String, property: KMutableProperty0<Int>) = add(NodeProperty.Swatch(label, property::get, property::set))

    /** One multi-select row of on/off [flags]. */
    fun flags(label: String, flags: List<Flag>) {
        if (flags.isEmpty()) return
        add(
            NodeProperty.Flags(
                label, flags.map { it.id },
                { flags.filter { it.get() }.mapTo(LinkedHashSet()) { it.id } },
                { chosen -> flags.forEach { if ((it.id in chosen) != it.get()) it.set(it.id in chosen) } },
                labelOf = { id -> flags.first { it.id == id }.label },
                none = I18n.trOr("blackaddons.actions.waypoint.triggers_none", "None"),
            ),
        )
    }

    /** Movement key chips. [property] holds the chip ids. */
    fun movementKeys(label: String, property: KMutableProperty0<List<String>>) =
        add(NodeProperty.MovementKeys(label, property::get, property::set))

    fun buttons(vararg buttons: Pair<String, () -> Unit>) = add(NodeProperty.Buttons(buttons.toList()))

    fun note(text: String) = add(NodeProperty.Note(text))

    /** X, Y (unless it is null) and Z, followed by Here, Look and Round .5 buttons. */
    fun coordinates(x: KMutableProperty0<Double>, y: KMutableProperty0<Double>?, z: KMutableProperty0<Double>) {
        number("X", x, COORD_STEP)
        y?.let { number("Y", it, COORD_STEP) }
        number("Z", z, COORD_STEP)
        val place = { px: Double, py: Double, pz: Double ->
            x.set(px.roundToDecimals(CAPTURE_DECIMALS))
            y?.set(py.roundToDecimals(CAPTURE_DECIMALS))
            z.set(pz.roundToDecimals(CAPTURE_DECIMALS))
        }
        val here = { Platform.player.pos.let { place(it.x, it.y, it.z) } }
        buttons(
            tr("here", "Here") to here,
            tr("look", "Look") to {
                val target = Platform.interaction.crosshairBlock
                if (target != null) place(target.x + HALF, target.y.toDouble(), target.z + HALF) else here()
            },
            tr("round", "Round .5") to {
                x.set(floor(x.get()) + HALF)
                z.set(floor(z.get()) + HALF)
            },
        )
    }

    /** Yaw (wrapped) and pitch (clamped), followed by a Capture button that copies your view. */
    fun rotation(yaw: KMutableProperty0<Float>, pitch: KMutableProperty0<Float>) {
        number(tr("yaw", "Yaw"), { yaw.get().toDouble() }, { yaw.set(ViewAngles.wrapYaw(it.toFloat())) }, 1.0, decimals = 1)
        number(tr("pitch", "Pitch"), { pitch.get().toDouble() }, { pitch.set(clampPitch(it.toFloat())) }, 1.0, decimals = 1)
        buttons(
            tr("capture", "Capture") to {
                yaw.set(ViewAngles.wrapYaw(Platform.player.yaw).roundToDecimals(CAPTURE_DECIMALS))
                pitch.set(clampPitch(Platform.player.pitch).roundToDecimals(CAPTURE_DECIMALS))
            },
        )
    }

    class Flag(val id: String, val label: String, val get: () -> Boolean, val set: (Boolean) -> Unit) {
        constructor(id: String, label: String, property: KMutableProperty0<Boolean>) : this(id, label, property::get, property::set)
    }

    private companion object {
        const val COORD_STEP = 0.5
        const val CAPTURE_DECIMALS = 2
        const val HALF = 0.5
        const val MAX_PITCH = 90f

        // The row texts of the actions editor, the main user of these rows.
        val tr = I18n.scope("blackaddons.actions.graph.prop")

        fun clampPitch(pitch: Float): Float = pitch.coerceIn(-MAX_PITCH, MAX_PITCH)

        fun Float.roundToDecimals(decimals: Int): Float = toDouble().roundToDecimals(decimals).toFloat()
    }
}
