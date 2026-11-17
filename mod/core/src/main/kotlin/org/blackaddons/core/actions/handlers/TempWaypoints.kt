package org.blackaddons.core.actions.handlers
import org.blackaddons.api.Platform
import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.core.actions.steps.TempWaypointStep
import java.awt.Color
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Waypoints from TEMP_WAYPOINT steps. Removed on arrival, timeout or world change (coords are per lobby). */
internal object TempWaypoints {
    private val NUMBER = Regex("""-?\d+(?:\.\d+)?""")

    private class Live(
        val label: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val color: Color,
        val beacon: Boolean,
        val tracer: Boolean,
        val throughBlocks: Boolean,
        val arrivalRadius: Double,
        val expiresAt: Long,
    )

    private val live = mutableListOf<Live>()

    fun init() {
        EventBus.subscribe<ClientTickEvent.Post> { onTick() }
        EventBus.subscribe<RenderWorldEvent> { onRender() }
        EventBus.subscribe<WorldChangeEvent> { live.clear() }
    }

    fun add(step: TempWaypointStep.Params, context: StepContext) {
        val (x, y, z) = parse(context.interpolate(step.waypointCoords)) ?: return
        live.removeIf { it.x == x && it.y == y && it.z == z }
        live += Live(
            label = context.interpolate(step.message).trim(),
            x = x, y = y, z = z,
            color = Color(step.waypointColor or 0xFF000000.toInt(), true),
            beacon = step.waypointBeacon,
            tracer = step.waypointTracer,
            throughBlocks = !step.waypointDepthCheck,
            arrivalRadius = step.waypointArrivalRadius,
            expiresAt = if (step.waypointExpireSeconds > 0f) System.currentTimeMillis() + (step.waypointExpireSeconds * 1000f).toLong() else Long.MAX_VALUE,
        )
    }

    /** First three numbers, centered on the block. Null if there are fewer. */
    internal fun parse(text: String): List<Double>? {
        val numbers = NUMBER.findAll(text).take(3).map { it.value.toDouble() }.toList()
        if (numbers.size < 3) return null
        return listOf(floor(numbers[0]) + 0.5, floor(numbers[1]), floor(numbers[2]) + 0.5)
    }

    private fun onTick() {
        if (live.isEmpty() || !Platform.player.isInGame) return
        val now = System.currentTimeMillis()
        live.removeIf { now >= it.expiresAt || (it.arrivalRadius > 0.0 && distanceTo(it) <= it.arrivalRadius) }
    }

    private fun onRender() {
        if (live.isEmpty() || !Platform.player.isInGame) return
        val renderer = Platform.worldRenderer
        for (wp in live) {
            if (wp.beacon && renderer.isVisible(wp.x - 0.5, -64.0, wp.z - 0.5, wp.x + 0.5, 320.0, wp.z + 0.5)) {
                renderer.beacon(wp.x, wp.z, wp.color, throughBlocks = wp.throughBlocks)
            }
            if (wp.tracer) renderer.tracer(wp.x, wp.y + 0.5, wp.z, wp.color, throughBlocks = wp.throughBlocks)
            val distance = "${distanceTo(wp).roundToInt()}m"
            val text = if (wp.label.isEmpty()) distance else "${wp.label} ($distance)"
            renderer.text(text, wp.x, wp.y + 1.5, wp.z, wp.color, 1.2f, wp.throughBlocks)
        }
    }

    private fun distanceTo(wp: Live): Double {
        val dx = Platform.player.x - wp.x
        val dy = Platform.player.y - wp.y
        val dz = Platform.player.z - wp.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
