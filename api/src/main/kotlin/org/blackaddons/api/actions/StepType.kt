package org.blackaddons.api.actions

import org.blackaddons.api.ui.node.NodeProperty
import org.blackaddons.api.world.Vec3d

/**
 * A kind of action step. Declare it as an `object` in your addon's package, and it is found like a feature. [P] is
 * saved as flat fields next to the common step fields, so every field needs a default value (a no-arg constructor),
 * must be mutable so the editor can write it, and must not share a name with a common field.
 */
abstract class StepType<P : Any>(
    /** Saved in action profiles, so never rename it. Addons use `"<mod id>:<name>"`. */
    val id: String,
) {
    /** Shown in menus, node headers and command replies. */
    abstract val displayName: String

    open val category: StepCategory get() = StepCategory.OUTPUT

    /** Params of a newly added step. Saved steps take missing fields from [P]'s own defaults instead. */
    abstract fun newParams(): P

    /** Editor rows, shown by both the graph and the step editor. The accessors must read and write [params]. */
    open fun properties(params: P): List<NodeProperty> = emptyList()

    /** One line for step lists. */
    open fun summary(params: P): String = ""

    /** How many ticks the step counts as running, so later steps with "wait for previous" wait for it too. */
    open fun busyTicks(params: P): Int = 0

    /** True while work this type started is still running. Holds back every step with "wait for previous" on. */
    open fun isBusy(): Boolean = false

    /** Runs on the client thread. If it throws, only this step is skipped. */
    abstract fun execute(params: P, context: StepContext)

    final override fun toString(): String = id
}

/** Picks the node color in the graph and orders the add-step menu. */
enum class StepCategory { WAIT, MOVEMENT, ITEM, OUTPUT, CONTROL }

interface StepContext {
    /** The other player who triggered the steps, or null for you. */
    val player: String?

    /** The waypoint whose action runs, or null. */
    val waypoint: Vec3d?

    /** Fills `{player}`, `{1}`, `{2}`... from the trigger. */
    fun interpolate(text: String): String
}

/** Params of a type that has none. */
class NoParams {
    override fun equals(other: Any?): Boolean = other is NoParams
    override fun hashCode(): Int = 0
}
