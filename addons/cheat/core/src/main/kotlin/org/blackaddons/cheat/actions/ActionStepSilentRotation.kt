package org.blackaddons.cheat.actions

import org.blackaddons.api.actions.StepType

internal class ActionStepSilentRotation(
    private val begin: () -> Unit,
    private val finish: () -> Unit,
    private val discard: () -> Unit,
    private val silentAlignEnabled: () -> Boolean,
    private val snapVisualAfterAlign: () -> Boolean,
    private val smoothReset: () -> Unit = discard,
    private val isSmoothResetEnabled: () -> Boolean = { false },
) {
    private var active = false
    private var activeStepType: StepType<*>? = null

    /** Called before each step with its [type], and with null once the steps are over. */
    fun onStepChanged(type: StepType<*>?, noRotateUntilNextStep: Boolean) {
        val shouldRemainSilent = when (type) {
            AlignStep -> silentAlignEnabled()
            null -> false
            else -> noRotateUntilNextStep
        }
        if (shouldRemainSilent != active) {
            if (shouldRemainSilent) {
                begin()
            } else if (activeStepType === AlignStep && !snapVisualAfterAlign()) {
                if (isSmoothResetEnabled()) {
                    smoothReset()
                } else {
                    discard()
                }
            } else {
                finish()
            }
            active = shouldRemainSilent
        }
        activeStepType = type?.takeIf { shouldRemainSilent }
    }
}
