package org.blackaddons.core.actions.steps

import org.blackaddons.api.actions.NoParams
import org.blackaddons.api.actions.StepCategory
import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.ui.node.NodeProperty
import org.blackaddons.api.ui.node.nodeProperties
import org.blackaddons.core.actions.ActionCondition
import org.blackaddons.core.ui.screen.actions.actionTr

/** The executor runs these itself, their own [StepType.execute] does nothing. They run for anyone. */
internal sealed interface ControlStep

/** Opens a block that runs up to its [EndStep]. */
internal sealed interface BlockStep : ControlStep

/** Params that carry the condition of an If or a "repeat while". */
internal interface Conditional {
    val condition: ActionCondition?
}

internal object RepeatStep : StepType<RepeatStep.Params>("REPEAT"), BlockStep {

    data class Params(
        /** Ignored when [repeatWhile] is on. */
        var repeatCount: Int = 2,
        var repeatWhile: Boolean = false,
        override var condition: ActionCondition? = null,
    ) : Conditional

    private const val MAX_REPEAT = 1000.0

    override val displayName: String get() = actionTr("step_type.repeat", "Repeat")
    override val category: StepCategory get() = StepCategory.CONTROL

    override fun newParams() = Params()

    override fun execute(params: Params, context: StepContext) {}

    override fun summary(params: Params): String =
        if (params.repeatWhile) actionTr("steps.while", "while %s", params.condition.describe()) else "×${params.repeatCount}"

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        val modes = listOf(actionTr("step.repeat_count_mode", "Count"), actionTr("step.repeat_while", "While Condition"))
        choice(prop("mode", "Mode"), modes, { if (params.repeatWhile) 1 else 0 }) { params.repeatWhile = it == 1 }
        if (params.repeatWhile) {
            condition(params::condition) { params.condition = it }
            note(actionTr("step.repeat_while_help", "Checks before every pass; stops after 10000 passes."))
        } else {
            number(prop("times", "Times"), params::repeatCount, 1.0, MAX_REPEAT)
        }
        note(actionTr("step.block_help", "Runs the steps up to its End."))
    }
}

internal object IfStep : StepType<IfStep.Params>("IF"), BlockStep {

    data class Params(override var condition: ActionCondition? = null) : Conditional

    override val displayName: String get() = actionTr("step_type.if", "If")
    override val category: StepCategory get() = StepCategory.CONTROL

    override fun newParams() = Params()

    override fun execute(params: Params, context: StepContext) {}

    override fun summary(params: Params): String = params.condition.describe()

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        condition(params::condition) { params.condition = it }
        note(actionTr("step.if_help", "Runs the steps up to its Else or End only when the condition holds. The steps after the End always run."))
    }
}

/** Goes between an If and its End. The steps after it run when the condition isn't met. */
internal object ElseStep : StepType<NoParams>("ELSE"), ControlStep {
    override val displayName: String get() = actionTr("step_type.else", "Else")
    override val category: StepCategory get() = StepCategory.CONTROL

    override fun newParams() = NoParams()

    override fun execute(params: NoParams, context: StepContext) {}

    override fun properties(params: NoParams): List<NodeProperty> = nodeProperties {
        note(actionTr("step.else_help", "Runs the steps up to the End when the If's condition does not hold."))
    }
}

internal object EndStep : StepType<NoParams>("END"), ControlStep {
    override val displayName: String get() = actionTr("step_type.end", "End")
    override val category: StepCategory get() = StepCategory.CONTROL

    override fun newParams() = NoParams()

    override fun execute(params: NoParams, context: StepContext) {}

    override fun properties(params: NoParams): List<NodeProperty> = nodeProperties {
        note(actionTr("step.end_help", "Closes the nearest Repeat or If above it."))
    }
}

internal object StopStepsStep : StepType<NoParams>("STOP_STEPS"), ControlStep {
    override val displayName: String get() = actionTr("step_type.stop_steps", "Stop Steps")
    override val category: StepCategory get() = StepCategory.CONTROL

    override fun newParams() = NoParams()

    override fun execute(params: NoParams, context: StepContext) {}

    override fun properties(params: NoParams): List<NodeProperty> = nodeProperties {
        note(actionTr("step.stop_steps_help", "Stops the rest of these steps. Steps another trigger started go on."))
    }
}
