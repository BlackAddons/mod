package org.blackaddons.api.actions

/**
 * An on/off option that an addon adds to the steps it [appliesTo], like the cheat edition's "no rotate until next
 * step". Declare it as an `object` in your addon's package, and it is found like a [StepType]. A step saves it as
 * `"<id>": true` next to its other fields, so never rename [id].
 */
abstract class StepFlag(val id: String) {
    /** Shown as the option's name in the editors. */
    abstract val label: String

    /** Control steps (If, Repeat...) do not get it by default. */
    open fun appliesTo(type: StepType<*>): Boolean = type.category != StepCategory.CONTROL

    final override fun toString(): String = id
}
