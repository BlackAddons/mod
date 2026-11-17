package org.blackaddons.core.actions.steps

import com.google.gson.annotations.SerializedName
import org.blackaddons.api.actions.StepCategory
import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.ui.node.NodeProperty
import org.blackaddons.api.ui.node.nodeProperties
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.secondsToTicks
import org.blackaddons.core.actions.ActionDungeonEvent
import org.blackaddons.core.actions.WaitMode
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.translatedName

/**
 * The executor does the waiting itself: a time wait holds the queue, and a condition wait blocks the step at the front.
 */
internal object WaitStep : StepType<WaitStep.Params>("WAIT") {

    data class Params(
        var durationSeconds: Float = 0f,
        var waitMode: WaitMode = WaitMode.TIME,
        // Only the waypoint path preview reads this. The wait always uses the target.
        var useCoordinates: Boolean = false,
        var targetX: Double = 0.0,
        var targetY: Double = 0.0,
        var targetZ: Double = 0.0,
        var waitRadius: Double = 1.0,
        var waitTimeoutSeconds: Float = 0f,
        var stopOnTimeout: Boolean = false,
        var waitText: String = "",
        var waitRegex: Boolean = false,
        var waitEvent: ActionDungeonEvent = ActionDungeonEvent.RUN_START,
    ) {
        @SerializedName("waitPosition") private var legacyWaitPosition: Boolean? = null

        val durationTicks: Int get() = durationSeconds.secondsToTicks

        fun adoptLegacy() {
            if (legacyWaitPosition == true) waitMode = WaitMode.POSITION
            legacyWaitPosition = null
        }
    }

    override val displayName: String get() = actionTr("step_type.wait", "Wait / Delay")
    override val category: StepCategory get() = StepCategory.WAIT

    override fun newParams() = Params()

    override fun busyTicks(params: Params): Int = if (params.waitMode.isCondition) 0 else params.durationTicks.coerceAtLeast(1)

    override fun execute(params: Params, context: StepContext) {}

    override fun summary(params: Params): String = when (params.waitMode) {
        WaitMode.TIME -> "${params.durationSeconds.formatDecimals(2)}s"
        WaitMode.POSITION -> "X:${params.targetX.toInt()} Y:${params.targetY.toInt()} Z:${params.targetZ.toInt()} (r=${params.waitRadius.formatDecimals(1)}m)"
        WaitMode.CHAT, WaitMode.BOSS_BAR -> "${params.waitMode.translatedName}: \"${params.waitText}\""
        WaitMode.DUNGEON_EVENT -> params.waitEvent.translatedName
    }

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        choice(prop("wait_for", "Wait for"), WaitMode.entries.map { it.translatedName }, { params.waitMode.ordinal }) {
            params.waitMode = WaitMode.entries[it]
            if (params.waitMode == WaitMode.POSITION) params.useCoordinates = true
        }
        when (params.waitMode) {
            WaitMode.TIME -> {
                number(prop("duration", "Duration"), params::durationSeconds, 0.05, min = 0.0)
                note(actionTr("step.wait_help", "Waits for the specified duration before proceeding to the next step."))
            }
            WaitMode.POSITION -> {
                coordinates(params::targetX, params::targetY, params::targetZ)
                number(actionTr("radius", "Radius"), params::waitRadius, 0.5, min = 0.0)
            }
            WaitMode.CHAT, WaitMode.BOSS_BAR -> {
                val placeholder = if (params.waitMode == WaitMode.BOSS_BAR) actionTr("step.boss_bar_placeholder", "Boss bar text, e.g. Maxor")
                else actionTr("step.chat_placeholder", "Chat text or regex")
                text(prop("text", "Text"), params::waitText, placeholder)
                toggle(prop("regex", "Regex"), params::waitRegex)
                note(actionTr("step.wait_signal_help", "Counts from the moment the previous step starts."))
            }
            WaitMode.DUNGEON_EVENT -> {
                choice(prop("event", "Event"), ActionDungeonEvent.entries, { it.translatedName }, params::waitEvent)
                note(actionTr("step.wait_signal_help", "Counts from the moment the previous step starts."))
            }
        }
        if (params.waitMode.isCondition) {
            number(prop("timeout", "Timeout"), params::waitTimeoutSeconds, 0.5, min = 0.0, decimals = 1)
            toggle(prop("stop_on_timeout", "Stop on timeout"), params::stopOnTimeout)
            note(actionTr("step.timeout_help", "No timeout waits forever. On timeout the steps go on, or stop if set."))
        }
    }
}
