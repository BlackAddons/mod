package org.blackaddons.core.actions.steps

import org.blackaddons.api.Platform
import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.ui.node.NodeProperty
import org.blackaddons.api.ui.node.NodeRows
import org.blackaddons.api.ui.node.nodeProperties
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.core.actions.execution.ActionHudTimer
import org.blackaddons.core.actions.execution.ActionTextOverlay
import org.blackaddons.core.actions.handlers.TempWaypoints
import org.blackaddons.core.ui.screen.actions.actionTr

internal object HudTimerStep : StepType<HudTimerStep.Params>("HUD_TIMER") {

    data class Params(var message: String = "", var durationSeconds: Float = 0f)

    override val displayName: String get() = actionTr("step_type.hud_timer", "HUD Timer")

    override fun newParams() = Params()

    override fun execute(params: Params, context: StepContext) =
        ActionHudTimer.start(context.interpolate(params.message), params.durationSeconds)

    override fun summary(params: Params): String =
        "${params.message.ifBlank { actionTr("steps.timer", "Timer") }}: ${params.durationSeconds.formatDecimals(2)}s"

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        number(prop("duration", "Duration"), params::durationSeconds, 0.05, min = 0.0)
        text(prop("timer_name", "Timer name"), params::message, actionTr("step.timer_name_placeholder", "Timer name"))
    }
}

internal object TextOverlayStep : StepType<TextOverlayStep.Params>("TEXT_OVERLAY") {

    data class Params(var message: String = "", var durationSeconds: Float = 0f)

    override val displayName: String get() = actionTr("step_type.text_overlay", "Text Overlay")

    override fun newParams() = Params(durationSeconds = 1f)

    override fun execute(params: Params, context: StepContext) =
        ActionTextOverlay.start(context.interpolate(params.message), params.durationSeconds)

    override fun summary(params: Params): String = "${params.message} (${params.durationSeconds.formatDecimals(2)}s)"

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        number(prop("show_for", "Show for"), params::durationSeconds, 0.05, min = 0.0)
        text(prop("text", "Text"), params::message, actionTr("step.overlay_text_placeholder", "{player} at ss"))
    }
}

internal object SendMessageStep : StepType<SendMessageStep.Params>("SEND_MESSAGE") {

    data class Params(var message: String = "")

    override val displayName: String get() = actionTr("step_type.send_message", "Send Message")

    override fun newParams() = Params()

    override fun execute(params: Params, context: StepContext) {
        val message = context.interpolate(params.message)
        if (message.startsWith("/")) Platform.chat.sendCommand(message.substring(1))
        else if (message.isNotEmpty()) Platform.chat.send(message)
    }

    override fun summary(params: Params): String = params.message

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        text(prop("message", "Message"), params::message, actionTr("step.message_placeholder", "Message or /command..."))
    }
}

/** A waypoint that goes away on arrival, timeout or world change. */
internal object TempWaypointStep : StepType<TempWaypointStep.Params>("TEMP_WAYPOINT") {

    data class Params(
        var message: String = "",
        /** "x y z" after the placeholders are filled. */
        var waypointCoords: String = "{1} {2} {3}",
        var waypointColor: Int = 0x55FFFF,
        var waypointBeacon: Boolean = true,
        var waypointTracer: Boolean = true,
        var waypointDepthCheck: Boolean = false,
        /** Removed within this many blocks. 0 means never. */
        var waypointArrivalRadius: Double = 8.0,
        /** Removed after this many seconds. 0 means on world change. */
        var waypointExpireSeconds: Float = 60f,
    )

    override val displayName: String get() = actionTr("step_type.temp_waypoint", "Temp Waypoints")

    override fun newParams() = Params()

    override fun execute(params: Params, context: StepContext) = TempWaypoints.add(params, context)

    override fun summary(params: Params): String = "${params.waypointCoords} ${params.message}"

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        text(prop("coords", "Coords"), params::waypointCoords, "{1} {2} {3}")
        text(prop("label", "Label"), params::message, "{1}")
        swatch(prop("color", "Color"), params::waypointColor)
        flags(
            prop("show", "Show"),
            listOf(
                NodeRows.Flag("beacon", prop("beacon", "Beacon"), params::waypointBeacon),
                NodeRows.Flag("tracer", prop("tracer", "Tracer"), params::waypointTracer),
                NodeRows.Flag("depth_check", prop("depth_check", "Depth check"), params::waypointDepthCheck),
            ),
        )
        number(prop("remove_within", "Remove within"), params::waypointArrivalRadius, 0.5, min = 0.0, decimals = 1)
        number(prop("remove_after", "Remove after"), params::waypointExpireSeconds, 1.0, min = 0.0, decimals = 1)
        note(actionTr("step.waypoint_help", "Also removed when you change worlds."))
    }
}
