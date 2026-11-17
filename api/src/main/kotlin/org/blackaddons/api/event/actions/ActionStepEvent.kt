package org.blackaddons.api.event.actions

import org.blackaddons.api.actions.StepFlag
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.event.Event

sealed class ActionStepEvent : Event() {
    /** Before the step runs. */
    class Started(val type: StepType<*>, private val flags: Set<String>) : ActionStepEvent() {
        /** Whether the step has [flag] set. */
        fun has(flag: StepFlag): Boolean = flag.id in flags
    }

    /** The last step finished and nothing is queued. */
    object Idle : ActionStepEvent()

    /**
     * The steps were cancelled: a screen opened, someone ran `/b ac stop`, or code called
     * [org.blackaddons.api.actions.ActionQueue.cancel].
     */
    object Cancelled : ActionStepEvent()
}
