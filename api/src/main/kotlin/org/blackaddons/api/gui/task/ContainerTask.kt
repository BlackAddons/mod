package org.blackaddons.api.gui.task

import org.blackaddons.api.world.ItemInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface ContainerTaskResult {
    data object Success : ContainerTaskResult

    /** Another task was running, so this one never started. */
    data object Busy : ContainerTaskResult
    data object Cancelled : ContainerTaskResult
    data class Failure(val reason: String) : ContainerTaskResult
}

/**
 * How a task treats the player. [silent] opens its menus without a screen. [stopMovement] and [cancelOnInput] apply
 * only while such a hidden menu is open. Each click waits a random time in [clickDelay].
 */
data class ContainerTaskOptions(
    val silent: Boolean = true,
    val stopMovement: Boolean = true,
    val cancelOnInput: Boolean = false,
    val clickDelay: ClosedRange<Duration> = 50.milliseconds..100.milliseconds,
)

/**
 * Steps that [ContainerTasks] runs in order. Only one task runs at a time, and each task runs once. Build one with
 * [containerTask].
 */
class ContainerTask internal constructor(
    val name: String,
    internal val steps: List<ContainerStep>,
    val options: ContainerTaskOptions,
    private val onFinished: (ContainerTaskResult) -> Unit,
) {
    var result: ContainerTaskResult? = null
        private set

    val isFinished: Boolean get() = result != null

    var completedSteps = 0
        internal set

    val totalSteps: Int get() = steps.size

    /** False if it did not start, and then [result] says why. Call it on the client thread. */
    fun run(): Boolean = ContainerTasks.run(this)

    fun cancel() = ContainerTasks.cancel(this)

    internal fun finish(result: ContainerTaskResult) {
        if (this.result != null) return
        this.result = result
        onFinished(result)
    }
}

internal sealed interface ContainerStep {
    class Open(val command: String, val title: Regex) : ContainerStep

    class Click(
        val inChest: Boolean,
        val match: (ItemInfo) -> Boolean,
        val quickMove: Boolean,
        val skipIf: (ItemInfo) -> Boolean,
        val failure: String,
    ) : ContainerStep

    class CatchReopen(val title: Regex) : ContainerStep
}

class ContainerTaskBuilder internal constructor() {
    internal val steps = mutableListOf<ContainerStep>()
    internal var onFinished: (ContainerTaskResult) -> Unit = {}

    /** Runs [command] (no slash) and waits for a menu whose title [title] matches, with its items. */
    fun open(command: String, title: Regex) {
        steps += ContainerStep.Open(command, title)
    }

    fun open(command: String, title: String) = open(command, Regex.fromLiteral(title))

    /**
     * Left-clicks the first menu slot that [match] accepts. If [skipIf] accepts that slot, it is left alone and
     * the step succeeds.
     */
    fun click(failure: String, skipIf: (ItemInfo) -> Boolean = { false }, match: (ItemInfo) -> Boolean) {
        steps += ContainerStep.Click(inChest = true, match, quickMove = false, skipIf, failure)
    }

    /** Shift-clicks the first of the player's own slots that [match] accepts. */
    fun quickMoveFromInventory(failure: String, match: (ItemInfo) -> Boolean) {
        steps += ContainerStep.Click(inChest = false, match, quickMove = true, skipIf = { false }, failure)
    }

    /**
     * Refuses the server's next reopen of [title], even after the task ends. Put it after the click that causes
     * the reopen.
     */
    fun catchReopen(title: String) {
        steps += ContainerStep.CatchReopen(Regex.fromLiteral(title))
    }

    /** Called once with every outcome, [ContainerTaskResult.Busy] included. */
    fun onFinished(block: (ContainerTaskResult) -> Unit) {
        onFinished = block
    }
}

fun containerTask(
    name: String,
    options: ContainerTaskOptions = ContainerTaskOptions(),
    build: ContainerTaskBuilder.() -> Unit,
): ContainerTask {
    val builder = ContainerTaskBuilder().apply(build)
    return ContainerTask(name, builder.steps.toList(), options, builder.onFinished)
}
