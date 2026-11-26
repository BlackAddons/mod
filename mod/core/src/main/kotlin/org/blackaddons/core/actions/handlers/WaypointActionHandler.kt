package org.blackaddons.core.actions.handlers
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.EntitySnapshot
import org.blackaddons.api.event.world.GuiEvent
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.WaitMode
import org.blackaddons.core.actions.WaypointDefinition
import org.blackaddons.core.actions.WaypointShape
import org.blackaddons.core.actions.execution.ActionRunner
import org.blackaddons.core.actions.steps.WaitStep
import org.blackaddons.core.render.WorldRenderPrefs
import java.awt.Color
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal object WaypointActionHandler {
    private const val OTHERS_RANGE = 128.0

    private var playerInsideState = HashMap<Pair<UUID, String>, Boolean>()
    private val lastTriggerTimes = mutableMapOf<Pair<UUID, String>, TimeSource.Monotonic.ValueTimeMark>()

    fun init() {
        EventBus.subscribe<ClientTickEvent.Post> { onTick() }
        EventBus.subscribe<RenderWorldEvent> { onRenderWorld() }
        EventBus.subscribe<GuiEvent.Close> { onGuiClose() }
    }

    private fun onTick() {
        if (!Platform.player.isInGame) {
            playerInsideState.clear()
            return
        }

        val px = Platform.player.x
        val py = Platform.player.y
        val pz = Platform.player.z
        val self = Platform.player.name.orEmpty()
        val others = if (ActionStorage.waypoints.any { it.watchesOthers && isWaypointActive(it) }) otherPlayers(px, py, pz) else emptyList()

        val seen = HashMap<Pair<UUID, String>, Boolean>()
        for (wp in ActionStorage.waypoints) {
            val active = isWaypointActive(wp)
            track(wp, active, null, self, px, py, pz, seen)
            if (wp.watchesOthers) {
                for (other in others) track(wp, active, other.name, other.name, other.x, other.y, other.z, seen)
            }
        }
        playerInsideState = seen
    }

    private fun track(
        wp: WaypointDefinition,
        active: Boolean,
        player: String?,
        name: String,
        x: Double,
        y: Double,
        z: Double,
        seen: MutableMap<Pair<UUID, String>, Boolean>,
    ) {
        val key = wp.id to name
        if (!active) {
            seen[key] = false
            return
        }
        val inside = isInside(wp, x, y, z)
        seen[key] = inside
        val before = playerInsideState[key] ?: return
        if (before != inside) triggerActions(wp, player, name, isEntry = inside, isExit = !inside, isGuiExit = false)
    }

    private fun otherPlayers(x: Double, y: Double, z: Double): List<EntitySnapshot> {
        val self = Platform.player.uuid
        return Platform.world.entitiesNear(x, y, z, OTHERS_RANGE)
            .filter { it.isPlayer && it.uuid != self && Uuids.isAccount(it.uuid) }
    }

    private fun onGuiClose() {
        if (!Platform.player.isInGame) return

        val px = Platform.player.x
        val py = Platform.player.y
        val pz = Platform.player.z
        val self = Platform.player.name.orEmpty()

        for (wp in ActionStorage.waypoints) {
            if (!isWaypointActive(wp)) continue
            if (isInside(wp, px, py, pz)) {
                triggerActions(wp, null, self, isEntry = false, isExit = false, isGuiExit = true)
            }
        }
    }

    private fun isWaypointActive(wp: WaypointDefinition): Boolean {
        if (!wp.enabled) return false
        if (!ActionStorage.inActiveGroup(ActionStorage.waypointGroups, wp)) return false
        return true
    }

    private fun isInside(wp: WaypointDefinition, x: Double, y: Double, z: Double): Boolean {
        if (y < wp.y || y > wp.y + wp.height) return false
        val dx = x - wp.x
        val dz = z - wp.z
        return if (wp.shape == WaypointShape.BOX) {
            abs(dx) <= wp.radius && abs(dz) <= wp.radius
        } else {
            dx * dx + dz * dz <= wp.radius * wp.radius
        }
    }

    private fun triggerActions(wp: WaypointDefinition, player: String?, name: String, isEntry: Boolean, isExit: Boolean, isGuiExit: Boolean) {
        val key = wp.id to name
        if (isOnCooldown(wp, key)) return

        var triggeredAny = false
        var queuedActions = false
        for (action in wp.actions) {
            if (!action.enabled || !action.triggeredBy.allows(player)) continue
            if (isEntry && !action.triggerOnEntry) continue
            if (isExit && !action.triggerOnExit) continue
            if (isGuiExit && !action.triggerOnGuiExit) continue
            triggeredAny = true

            if (ActionRunner.run(
                    steps = action.actions,
                    feedback = action,
                    waypoint = wp,
                    clearPrevious = !queuedActions,
                    defaultNotificationTitle = wp.name.ifBlank { "Waypoint Action" },
                    player = player,
                )) {
                queuedActions = true
            }
        }

        if (triggeredAny) {
            lastTriggerTimes[key] = TimeSource.Monotonic.markNow()
        }
    }

    private fun isOnCooldown(wp: WaypointDefinition, key: Pair<UUID, String>): Boolean {
        if (wp.reuseCooldownSeconds <= 0f) return false
        val last = lastTriggerTimes[key] ?: return false
        return last.elapsedNow() < wp.reuseCooldownSeconds.toDouble().seconds
    }

    private fun onRenderWorld() {
        if (!Platform.player.isInGame) return

        val renderer = Platform.worldRenderer
        val throughBlocks = !WorldRenderPrefs.waypointDepthCheck()
        val depthPriority = !throughBlocks

        for (wp in ActionStorage.waypoints) {
            if (!isWaypointActive(wp)) continue

            val rawColor = wp.colorRgb
            val color = if ((rawColor and 0xFF000000.toInt()) == 0) Color(rawColor or 0xFF000000.toInt(), true) else Color(rawColor, true)

            if (wp.showBeacon && renderer.isVisible(wp.x - 0.5, -64.0, wp.z - 0.5, wp.x + 0.5, 320.0, wp.z + 0.5)) {
                renderer.beacon(wp.x, wp.z, color, throughBlocks = throughBlocks)
            }

            val x1 = wp.x - wp.radius
            val y1 = wp.y
            val z1 = wp.z - wp.radius
            val x2 = wp.x + wp.radius
            val y2 = wp.y + wp.height
            val z2 = wp.z + wp.radius
            val shapeVisible = renderer.isVisible(x1, y1, z1, x2, y2, z2)

            if (wp.showFullShape && shapeVisible) {
                val alpha = if (color.alpha == 255) 80 else color.alpha
                val filledColor = Color(color.red, color.green, color.blue, alpha)
                if (wp.shape == WaypointShape.BOX) {
                    renderer.filledBox(x1, y1, z1, x2, y2, z2, filledColor, throughBlocks, depthPriority)
                } else {
                    renderer.filledCylinder(wp.x, y1, wp.z, y2, wp.radius, filledColor, throughBlocks, depthPriority)
                }
            }

            if (wp.showShape && shapeVisible) {
                if (wp.shape == WaypointShape.BOX) {
                    renderer.boxEdges(x1, y1, z1, x2, y2, z2, color, 2f, throughBlocks, depthPriority)
                } else {
                    renderer.ring(wp.x, wp.y, wp.z, wp.radius, color, 2f, throughBlocks, depthPriority)
                    if (wp.showFullShape && wp.height > 0) {
                        renderer.ring(wp.x, y2, wp.z, wp.radius, color, 2f, throughBlocks, depthPriority)
                    }
                }
            }

            if (wp.showText && wp.name.isNotBlank()) {
                renderer.text(wp.name, wp.x, wp.y + wp.height + 0.5, wp.z, color, 1.2f, throughBlocks)
            }

            var hasWaitSteps = false
            for (action in wp.actions) {
                if (!action.enabled) continue
                for (step in action.actions) {
                    if (step.enabled && step.paramsOf(WaitStep)?.waitMode == WaitMode.POSITION) {
                        hasWaitSteps = true
                        break
                    }
                }
                if (hasWaitSteps) break
            }

            if (hasWaitSteps) {
                var lastX = wp.x
                var lastY = wp.y + 0.5
                var lastZ = wp.z

                for (action in wp.actions) {
                    if (!action.enabled) continue
                    for (entry in action.actions) {
                        val step = entry.paramsOf(WaitStep)?.takeIf { entry.enabled && it.waitMode == WaitMode.POSITION } ?: continue

                        val stepX = if (step.useCoordinates) step.targetX else wp.x
                        val stepY = if (step.useCoordinates) step.targetY else wp.y
                        val stepZ = if (step.useCoordinates) step.targetZ else wp.z
                        val stepRadius = if (step.waitRadius > 0.0) step.waitRadius else 1.0

                        val stepVisible = renderer.isVisible(
                            minOf(lastX, stepX - stepRadius), minOf(lastY, stepY), minOf(lastZ, stepZ - stepRadius),
                            maxOf(lastX, stepX + stepRadius), maxOf(lastY, stepY + 0.5), maxOf(lastZ, stepZ + stepRadius),
                        )
                        if (stepVisible) {
                            renderer.line(lastX, lastY, lastZ, stepX, stepY + 0.5, stepZ, color, 2.5f, throughBlocks, depthPriority)

                            renderer.ring(stepX, stepY, stepZ, stepRadius, color, 2f, throughBlocks, depthPriority)
                        }

                        lastX = stepX
                        lastY = stepY + 0.5
                        lastZ = stepZ
                    }
                }
            }
        }
    }
}
