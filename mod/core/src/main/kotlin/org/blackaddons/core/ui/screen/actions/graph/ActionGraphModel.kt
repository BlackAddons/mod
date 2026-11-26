package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.api.actions.StepType
import org.blackaddons.api.ui.node.*
import org.blackaddons.core.actions.*
import org.blackaddons.core.actions.execution.ActionBlocks
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.actions.steps.*
import org.blackaddons.core.ui.screen.actions.ActionsPage
import java.awt.Color

internal object ActionGraphSockets {
    val EXEC = SocketType("exec", Color(0xC9CED9))

    const val RUN = 0
    const val NEXT = 0
    const val BODY = 1
    const val TRUE = 0
    const val FALSE = 1
}

internal sealed class TriggerRef {
    abstract val key: String
    abstract val steps: MutableList<ActionStep>
    abstract var enabled: Boolean

    /** What the list groups: the action, or the waypoint for its actions. */
    abstract val owner: Groupable

    abstract fun detach(profile: ActionProfile)
    abstract fun attach(profile: ActionProfile)

    private var slot = 0

    protected fun <T> MutableList<T>.take(item: T) {
        val i = indexOfFirst { it === item }
        if (i < 0) return
        slot = i
        removeAt(i)
    }

    protected fun <T> MutableList<T>.restore(item: T) {
        if (none { it === item }) add(slot.coerceIn(0, size), item)
    }

    class Chat(val action: ChatAction) : TriggerRef() {
        override val key = "chat:${action.id}"
        override val owner get() = action
        override val steps get() = action.actions
        override var enabled
            get() = action.enabled
            set(value) {
                action.enabled = value
            }

        override fun detach(profile: ActionProfile) = profile.chatActions.take(action)
        override fun attach(profile: ActionProfile) = profile.chatActions.restore(action)
    }

    /** Or the waypoint itself while it has none ([pending]). The switch belongs to the waypoint, as in the list. */
    class Waypoint(val waypoint: WaypointDefinition, val action: WaypointAction, val index: Int, val pending: Boolean) : TriggerRef() {
        override val key = "wp:${waypoint.id}:$index"
        override val owner get() = waypoint
        override val steps get() = action.actions
        override var enabled
            get() = waypoint.enabled && action.enabled
            set(value) {
                waypoint.enabled = value
                if (value) action.enabled = true
            }

        override fun detach(profile: ActionProfile) = if (pending) profile.waypoints.take(waypoint) else waypoint.actions.take(action)
        override fun attach(profile: ActionProfile) = if (pending) profile.waypoints.restore(waypoint) else waypoint.actions.restore(action)
    }

    class Shortcut(val shortcut: ShortcutAction) : TriggerRef() {
        override val key = "sc:${shortcut.id}"
        override val owner get() = shortcut
        override val steps get() = shortcut.actions
        override var enabled
            get() = shortcut.enabled
            set(value) {
                shortcut.enabled = value
            }

        override fun detach(profile: ActionProfile) = profile.shortcuts.take(shortcut)
        override fun attach(profile: ActionProfile) = profile.shortcuts.restore(shortcut)
    }

    class Location(val action: LocationAction) : TriggerRef() {
        override val key = "loc:${action.id}"
        override val owner get() = action
        override val steps get() = action.actions
        override var enabled
            get() = action.enabled
            set(value) {
                action.enabled = value
            }

        override fun detach(profile: ActionProfile) = profile.locationActions.take(action)
        override fun attach(profile: ActionProfile) = profile.locationActions.restore(action)
    }

    companion object {
        fun of(profile: ActionProfile, page: ActionsPage): List<TriggerRef> = when (page) {
            ActionsPage.CHAT -> profile.chatActions.map(::Chat)
            ActionsPage.WAYPOINT -> profile.waypoints.flatMap { wp ->
                if (wp.actions.isEmpty()) listOf(Waypoint(wp, WaypointAction(), 0, pending = true))
                else wp.actions.mapIndexed { i, a -> Waypoint(wp, a, i, pending = false) }
            }
            ActionsPage.SHORTCUT -> profile.shortcuts.map(::Shortcut)
            ActionsPage.LOCATION -> profile.locationActions.map(::Location)
        }
    }
}

internal class TriggerPayload(val ref: TriggerRef)

internal class StepPayload(val step: ActionStep, var end: ActionStep?) {
    var lastType: StepType<*>? = step.type
}

/** An If has no output of its own after it. Whatever follows hangs off both branches, which join there. */
internal class ActionGraphSocketSet(run: String, input: String, next: String, after: String, isTrue: String, isFalse: String, loop: String) {
    val triggerOutputs = listOf(SocketSpec("run", run, ActionGraphSockets.EXEC))
    val stepInputs = listOf(SocketSpec("in", input, ActionGraphSockets.EXEC))
    val stepOutputs = listOf(SocketSpec("next", next, ActionGraphSockets.EXEC))
    val ifOutputs = listOf(SocketSpec("true", isTrue, ActionGraphSockets.EXEC), SocketSpec("false", isFalse, ActionGraphSockets.EXEC))
    val repeatOutputs = listOf(SocketSpec("next", after, ActionGraphSockets.EXEC), SocketSpec("body", loop, ActionGraphSockets.EXEC))

    fun outputsFor(type: StepType<*>?): List<SocketSpec> = when (type) {
        IfStep -> ifOutputs
        RepeatStep -> repeatOutputs
        else -> stepOutputs
    }

    companion object {
        val PLAIN = ActionGraphSocketSet("Run", "In", "Next", "After", "True", "False", "Loop")
    }
}

internal class LooseChain(val steps: MutableList<ActionStep>, val positions: MutableList<FloatArray>)

internal class ActionGraphBuilder(
    private val graph: NodeGraph,
    private val sockets: ActionGraphSocketSet,
    private val saved: Map<String, FloatArray> = emptyMap(),
    private val onNode: (GraphNode) -> Unit = {},
    private val stepHeight: (ActionStep) -> Float = { 0f },
) {
    private var cursorY = 0f
    private var bandBottom = 0f

    /** Keyed as [ActionGraphCompiler.Result.keys] returns them. */
    val keys = HashMap<String, Int>()

    init {
        graph.joins = { spec -> ActionGraphCompiler.joinsBranches(graph, spec) }
    }

    /** Lays out [triggers], then [loose]. A change of [band] starts a new band lower, with room for a frame. */
    fun build(triggers: List<TriggerRef>, loose: List<LooseChain> = emptyList(), band: (TriggerRef) -> Any? = { null }) {
        cursorY = if (saved.isEmpty()) 0f else saved.values.maxOf { it[1] } + ROW_H + TRIGGER_GAP
        var last: Any? = null
        triggers.forEachIndexed { i, ref ->
            val current = band(ref)
            if (i > 0 && current != last) cursorY += BAND_GAP
            last = current
            addTrigger(ref)
        }
        if (triggers.isNotEmpty() && last != null) cursorY += BAND_GAP
        loose.forEachIndexed(::addLoose)
    }

    private fun addTrigger(ref: TriggerRef): GraphNode {
        val pos = saved[ref.key] ?: floatArrayOf(0f, cursorY)
        val node = graph.addNode(emptyList(), sockets.triggerOutputs, pos[0], pos[1]) { payload = TriggerPayload(ref) }
        keys[ref.key] = node.id
        onNode(node)
        bandBottom = pos[1] + rowHeight(node.height)
        chain(ref.steps, 0, ref.steps.size, "${ref.key}/", listOf(node.id to ActionGraphSockets.RUN), pos[0] + COL_W, pos[1])
        cursorY = maxOf(cursorY, bandBottom + TRIGGER_GAP)
        return node
    }

    private fun addLoose(number: Int, chain: LooseChain) {
        val origin = chain.positions.firstOrNull() ?: floatArrayOf(0f, cursorY)
        bandBottom = origin[1] + ROW_H
        var index = 0
        val positions = chain.positions
        chain(chain.steps, 0, chain.steps.size, looseKey(number), emptyList(), origin[0], origin[1]) { positions.getOrNull(index++) }
        cursorY = maxOf(cursorY, bandBottom + TRIGGER_GAP)
    }

    /** Wires the chain to the outputs in [ends] and returns the outputs whatever follows it hangs off. */
    private fun chain(
        steps: List<ActionStep>, from: Int, until: Int, prefix: String?,
        ends: List<Pair<Int, Int>>, x: Float, y: Float,
        nextPosition: (() -> FloatArray?)? = null,
    ): List<Pair<Int, Int>> {
        var open = ends
        var column = x
        var ordinal = 0
        var i = from
        bandBottom = maxOf(bandBottom, rowBottom(steps, from, until, y))
        while (i < until) {
            val step = steps[i]
            // These come from the wires: an END closes the block, and an ELSE is the If's Else output.
            if (step.type === EndStep || step.type === ElseStep) {
                i++
                continue
            }
            val key = prefix?.let { "$it$ordinal" }
            val pos = nextPosition?.invoke() ?: key?.let { saved[it] } ?: floatArrayOf(column, y)
            val block = step.type is BlockStep
            val end = if (block) minOf(ActionBlocks.endOf(steps, i), until) else i
            val closer = if (block && end < until) steps[end] else null
            val node = graph.addNode(sockets.stepInputs, sockets.outputsFor(step.type), pos[0], pos[1]) {
                payload = StepPayload(step, closer)
            }
            if (key != null) keys[key] = node.id
            onNode(node)
            open.forEach { (from, slot) -> graph.connect(from, slot, node.id, 0) }
            open = when {
                step.type === IfStep -> {
                    val otherwise = ActionBlocks.elseOf(steps, i)?.takeIf { it < end }
                    val onTrue = chain(steps, i + 1, otherwise ?: end, key?.let { "${it}b/" }, listOf(node.id to ActionGraphSockets.TRUE), column + COL_W, bandBottom, nextPosition)
                    val onFalse = if (otherwise == null) listOf(node.id to ActionGraphSockets.FALSE)
                    else chain(steps, otherwise + 1, end, key?.let { "${it}e/" }, listOf(node.id to ActionGraphSockets.FALSE), column + COL_W, bandBottom, nextPosition)
                    onTrue + onFalse
                }
                block -> {
                    chain(steps, i + 1, end, key?.let { "${it}b/" }, listOf(node.id to ActionGraphSockets.BODY), column + COL_W, bandBottom, nextPosition)
                    listOf(node.id to ActionGraphSockets.NEXT)
                }
                else -> listOf(node.id to ActionGraphSockets.NEXT)
            }
            i = if (block) end + 1 else i + 1
            column += COL_W
            ordinal++
        }
        return open
    }

    /* Uses the row's tallest step, built or not. */
    private fun rowBottom(steps: List<ActionStep>, from: Int, until: Int, y: Float): Float {
        var bottom = y
        var i = from
        while (i < until) {
            val step = steps[i]
            if (step.type !== EndStep && step.type !== ElseStep) bottom = maxOf(bottom, y + rowHeight(stepHeight(step)))
            i = if (step.type is BlockStep) minOf(ActionBlocks.endOf(steps, i), until) + 1 else i + 1
        }
        return bottom
    }

    private fun rowHeight(nodeHeight: Float): Float = maxOf(ROW_H, nodeHeight + ROW_GAP)

    companion object {
        fun looseKey(number: Int) = "loose:$number/"

        const val COL_W = 220f
        const val ROW_H = 96f
        const val ROW_GAP = 24f
        const val TRIGGER_GAP = 32f
        const val BAND_GAP = 72f
    }
}

internal object ActionGraphCompiler {

    /** Keys as [ActionGraphBuilder.keys] uses them. */
    class Result(val loose: List<LooseChain>, val positions: Map<String, FloatArray>, val looseSteps: Int, val keys: Map<Int, String>)

    fun flatten(graph: NodeGraph, head: Int?, visit: (nodeId: Int, key: String) -> Unit = { _, _ -> }, prefix: String = ""): MutableList<ActionStep> {
        val out = ArrayList<ActionStep>()
        emit(graph, head, null, out, depth = 0, prefix, visit, HashSet())
        return out
    }

    /**
     * An If becomes If, its True steps, Else, its False steps and End. What follows is the node where both branches
     * join.
     */
    private fun emit(
        graph: NodeGraph, head: Int?, until: Int?, out: MutableList<ActionStep>, depth: Int, prefix: String,
        visit: (Int, String) -> Unit, seen: MutableSet<Int>,
    ) {
        var current = head
        var ordinal = 0
        while (current != null && current != until) {
            if (!seen.add(current)) return
            val node = graph.node(current) ?: return
            val payload = node.payload as? StepPayload ?: return
            visit(current, "$prefix$ordinal")
            out += payload.step
            val after = when (payload.step.type) {
                IfStep -> joinOf(graph, current)
                else -> next(graph, current, ActionGraphSockets.NEXT)
            }
            if (payload.step.type is BlockStep) {
                val isIf = payload.step.type === IfStep
                emit(graph, next(graph, current, if (isIf) ActionGraphSockets.TRUE else ActionGraphSockets.BODY), if (isIf) after else null, out, depth + 1, "$prefix${ordinal}b/", visit, seen)
                val otherwise = if (isIf) next(graph, current, ActionGraphSockets.FALSE)?.takeIf { it != after } else null
                if (otherwise != null) {
                    out += ActionExecutor.newStep(ElseStep)
                    emit(graph, otherwise, after, out, depth + 1, "$prefix${ordinal}e/", visit, seen)
                }
                val end = payload.end ?: if (after != null || depth > 0) ActionExecutor.newStep(EndStep).also { payload.end = it } else null
                if (end != null) out += end
            }
            current = after
            ordinal++
        }
    }

    /** Where the If [node]'s True and False branches meet, counting [extra] as wired too. Null if they never meet. */
    fun joinOf(graph: NodeGraph, node: Int, extra: LinkSpec? = null): Int? {
        val onTrue = path(graph, next(graph, node, ActionGraphSockets.TRUE, extra), extra)
        val onFalse = path(graph, next(graph, node, ActionGraphSockets.FALSE, extra), extra).toHashSet()
        return onTrue.firstOrNull { it in onFalse }
    }

    /** True when [spec] is what makes its target the join of some If, so the target keeps its other wire. */
    fun joinsBranches(graph: NodeGraph, spec: LinkSpec): Boolean = graph.nodes().any {
        (it.payload as? StepPayload)?.step?.type === IfStep && joinOf(graph, it.id, spec) == spec.to && joinOf(graph, it.id) != spec.to
    }

    // The nodes that run one after another from [head] on one level. An If continues where its branches join.
    private fun path(graph: NodeGraph, head: Int?, extra: LinkSpec?): List<Int> {
        val nodes = ArrayList<Int>()
        var current = head
        while (current != null && current !in nodes) {
            nodes += current
            val payload = graph.node(current)?.payload as? StepPayload ?: break
            current = if (payload.step.type === IfStep) joinOf(graph, current, extra) else next(graph, current, ActionGraphSockets.NEXT, extra)
        }
        return nodes
    }

    fun next(graph: NodeGraph, node: Int, slot: Int, extra: LinkSpec? = null): Int? =
        extra?.takeIf { it.from == node && it.fromSlot == slot }?.to ?: graph.linksAt(node, slot, output = true).firstOrNull()?.to

    fun isHead(graph: NodeGraph, node: GraphNode): Boolean =
        node.inputs.isNotEmpty() && graph.linksAt(node.id, 0, output = false).isEmpty()

    fun compile(graph: NodeGraph, writeSteps: Boolean = true): Result {
        val positions = HashMap<String, FloatArray>()
        val keys = HashMap<Int, String>()
        val reached = HashSet<Int>()
        for (node in graph.nodes()) {
            val ref = (node.payload as? TriggerPayload)?.ref ?: continue
            positions[ref.key] = floatArrayOf(node.x, node.y)
            keys[node.id] = ref.key
            val steps = flatten(graph, next(graph, node.id, ActionGraphSockets.RUN), { id, key ->
                reached += id
                keys[id] = "${ref.key}/$key"
                graph.node(id)?.let { positions["${ref.key}/$key"] = floatArrayOf(it.x, it.y) }
            })
            if (!writeSteps) continue
            ref.steps.clear()
            ref.steps.addAll(steps)
            if (ref is TriggerRef.Waypoint && ref.pending && steps.isNotEmpty() && ref.waypoint.actions.none { it === ref.action }) {
                ref.waypoint.actions.add(ref.action)
            }
        }
        val loose = ArrayList<LooseChain>()
        var looseSteps = 0
        for (node in graph.nodes()) {
            if (node.id in reached || node.payload !is StepPayload || !isHead(graph, node)) continue
            val chainPositions = ArrayList<FloatArray>()
            val prefix = ActionGraphBuilder.looseKey(loose.size)
            val steps = flatten(graph, node.id, { id, key ->
                keys[id] = "$prefix$key"
                graph.node(id)?.let { chainPositions += floatArrayOf(it.x, it.y) }
            })
            looseSteps += chainPositions.size
            loose += LooseChain(steps, chainPositions)
        }
        return Result(loose, positions, looseSteps, keys)
    }

    fun makesCycle(graph: NodeGraph, spec: LinkSpec): Boolean {
        val stack = ArrayDeque<Int>()
        val seen = HashSet<Int>()
        stack.addLast(spec.to)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (id == spec.from) return true
            if (!seen.add(id)) continue
            val node = graph.node(id) ?: continue
            for (slot in node.outputs.indices) graph.linksAt(id, slot, output = true).forEach { stack.addLast(it.to) }
        }
        return false
    }
}
