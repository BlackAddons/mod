package org.blackaddons.core.actions

import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.actions.StepFlag
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.util.secondsToTicks

/** Step types that only record what ran, so tests need no game. */
internal object TestSteps {

    data class Params(var label: String = "", var durationSeconds: Float = 0f)

    class Recording(id: String) : StepType<Params>(id) {
        var busy = false

        override val displayName: String get() = id

        override fun newParams() = Params()

        override fun busyTicks(params: Params): Int = params.durationSeconds.secondsToTicks

        override fun isBusy(): Boolean = busy

        override fun execute(params: Params, context: StepContext) {
            ran += params.label.ifEmpty { id }
            lastContext = context
        }
    }

    object Flag : StepFlag("test:flag") {
        override val label: String get() = id
    }

    val TAP = Recording("test:tap").registered()
    val HOLD = Recording("test:hold").registered()

    val ran = mutableListOf<String>()
    var lastContext: StepContext? = null

    fun step(type: Recording = TAP, label: String = "", seconds: Float = 0f): ActionStep = ActionStep.of(type, Params(label, seconds))

    fun reset() {
        ran.clear()
        lastContext = null
        TAP.busy = false
        HOLD.busy = false
    }

    private fun Recording.registered(): Recording = apply { StepTypes.register(this)?.let(::error) }

    init {
        StepTypes.register(Flag)?.let(::error)
    }
}
