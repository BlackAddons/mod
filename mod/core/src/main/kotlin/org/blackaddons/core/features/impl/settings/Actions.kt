package org.blackaddons.core.features.impl.settings

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.hud.hudFormattedTextNode
import org.blackaddons.api.hud.hudText
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.ticksToSeconds
import org.blackaddons.core.actions.execution.ActionHudTimer
import org.blackaddons.core.actions.execution.ActionTextOverlay
import org.blackaddons.core.actions.handlers.LocationActionHandler
import org.blackaddons.core.render.WorldRenderPrefs

@AlwaysActive
object Actions : Feature(
    description = "Settings and options for actions and waypoint rendering.",
    name = "Actions",
) {
    @Suppress("unused")
    private val timerHud = hudElement(
        "Action Timer",
        shouldDraw = ActionHudTimer::isActive,
        defaultAnchorX = AnchorX.CENTER,
        defaultAnchorY = AnchorY.TOP,
        defaultOffsetY = 60f,
    ) { example ->
        val seconds = if (example) 10.0 else ActionHudTimer.remainingSeconds()
        hudFormattedTextNode("${ActionHudTimer.name}: ${seconds.formatDecimals(1)}s", Theme.TEXT_PRIMARY, shadow = true)
    }

    @Suppress("unused")
    private val textOverlayHud = hudElement(
        "Action Text",
        shouldDraw = ActionTextOverlay::isActive,
        defaultAnchorX = AnchorX.CENTER,
        defaultAnchorY = AnchorY.MIDDLE,
        defaultOffsetY = -105f,
        defaultScale = 2f,
    ) { example ->
        hudFormattedTextNode(if (example) "Player at ss" else ActionTextOverlay.name, Theme.TEXT_PRIMARY, shadow = true)
    }

    private val tickDebugger by boolean("Tick Debugger", default = false)
        .withDescription("Show server ticks since the F7 phase started and until each tick-delayed location action fires.")

    @Suppress("unused")
    private val tickHud = hudElement(
        "Action Ticks",
        enabled = { tickDebugger.value },
        shouldDraw = { LocationActionHandler.phase != null || LocationActionHandler.pendingTicks().isNotEmpty() },
        defaultOffsetX = 5f,
        defaultOffsetY = 120f,
    ) { example ->
        val lines = if (example) listOf("Maxor: ${ticks(41)}", "Crystals: ${ticks(42)}")
        else listOfNotNull(LocationActionHandler.phase?.let { "${it.boss}: ${ticks(LocationActionHandler.phaseTicks.toInt())}" }) +
            LocationActionHandler.pendingTicks().map { (action, left) -> "${action.name}: ${ticks(left.toInt())}" }
        column { for (line in lines) hudText(line, Theme.TEXT_PRIMARY, shadow = true) }
    }

    private val beaconSpin by number(
        name = "Beacon Speed",
        default = 1.0, min = 0.0, max = 5.0, step = 0.25, unit = "x",
    ).withDescription("How fast the beacon beam rotates around its axis. 0 stops it.")

    private val depthCheckSetting by boolean(
        name = "Depth Check",
        default = true,
    ).withDescription("Enable depth testing for waypoint rendering (hides waypoints behind blocks when enabled).")

    private val defaultView by mode("Default View", default = GRAPH_VIEW, modes = listOf(LIST_VIEW, GRAPH_VIEW))
        .withDescription("Which view /b ac and the Actions tab open: the list, or the node graph.")

    private val graphMinimapSetting by boolean("Graph Minimap", default = true).hidden()

    val opensGraph: Boolean get() = defaultView.value == GRAPH_VIEW
    var graphMinimap: Boolean
        get() = graphMinimapSetting.value
        set(value) {
            graphMinimapSetting.value = value
            Platform.config.save()
        }

    /** 0 means static. The platform renderer reads it each frame. */
    val beaconSpeed: Double get() = beaconSpin.value

    val depthCheck: Boolean get() = depthCheckSetting.value

    override fun init() {
        WorldRenderPrefs.beaconSpeed = { beaconSpeed }
        WorldRenderPrefs.waypointDepthCheck = { depthCheck }
    }

    private const val LIST_VIEW = "List"
    private const val GRAPH_VIEW = "Graph"

    private fun ticks(ticks: Int) = "${ticks}t (${ticks.ticksToSeconds.formatDecimals()}s)"
}
