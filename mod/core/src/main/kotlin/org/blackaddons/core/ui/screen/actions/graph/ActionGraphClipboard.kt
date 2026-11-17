package org.blackaddons.core.ui.screen.actions.graph

import com.google.gson.JsonParser
import org.blackaddons.api.ui.node.GraphNode
import org.blackaddons.api.ui.node.NodeGraph
import org.blackaddons.api.util.io.withoutNulls
import org.blackaddons.core.actions.ActionJson
import org.blackaddons.core.actions.ActionPortable
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.steps.ElseStep
import org.blackaddons.core.actions.steps.EndStep

/** Copied steps offset from the copy's top-left, wires as `[from, fromSlot, to]`. [blackaddonsSteps] marks it ours. */
internal class CopiedSteps(
    var blackaddonsSteps: Int = 1,
    var steps: MutableList<ActionStep> = mutableListOf(),
    var positions: MutableList<FloatArray> = mutableListOf(),
    var links: MutableList<IntArray> = mutableListOf(),
)

internal object ActionGraphClipboard {
    private val gson = ActionJson.gson()

    private const val MARKER = "blackaddonsSteps"
    private const val MAX_STEPS = 512

    fun copy(graph: NodeGraph, nodes: List<GraphNode>): CopiedSteps? {
        val steps = nodes.filter { it.payload is StepPayload }
        if (steps.isEmpty()) return null
        val index = steps.withIndex().associate { (i, node) -> node.id to i }
        val minX = steps.minOf { it.x }
        val minY = steps.minOf { it.y }
        return CopiedSteps(
            steps = steps.mapTo(mutableListOf()) { (it.payload as StepPayload).step },
            positions = steps.mapTo(mutableListOf()) { floatArrayOf(it.x - minX, it.y - minY) },
            links = steps.flatMap { graph.linksOf(it.id) }.distinctBy { it.id }
                .filter { it.from in index && it.to in index }
                .mapTo(mutableListOf()) { intArrayOf(index.getValue(it.from), it.fromSlot, index.getValue(it.to)) },
        )
    }

    fun write(copied: CopiedSteps): String = gson.toJson(copied)

    /** Shares no step objects with [copied]. */
    fun detached(copied: CopiedSteps): CopiedSteps = gson.fromJson(gson.toJsonTree(copied), CopiedSteps::class.java)

    fun read(text: String): CopiedSteps? {
        val json = runCatching { JsonParser.parseString(text) }.getOrNull()?.takeIf { it.isJsonObject } ?: return null
        if (!json.asJsonObject.has(MARKER)) return null
        return runCatching { gson.fromJson(json.withoutNulls(), CopiedSteps::class.java) }.getOrNull()?.takeIf(::valid)
    }

    private fun valid(copied: CopiedSteps): Boolean {
        val steps = copied.steps
        if (steps.isEmpty() || steps.size > MAX_STEPS || copied.positions.size != steps.size) return false
        if (!steps.all { ActionPortable.known(it) && it.type !== EndStep && it.type !== ElseStep }) return false
        if (!copied.positions.all { it.size >= 2 && it[0].isFinite() && it[1].isFinite() }) return false
        return copied.links.all { link ->
            link.size == 3 && link[0] in steps.indices && link[2] in steps.indices && link[0] != link[2] &&
                link[1] in ActionGraphSocketSet.PLAIN.outputsFor(steps[link[0]].type).indices
        }
    }
}
