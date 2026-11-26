package org.blackaddons.core.actions.execution

import org.blackaddons.api.Platform
import org.blackaddons.api.actions.ActionQueue
import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.actions.ActionStepEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.GuiEvent
import org.blackaddons.api.util.secondsToTicks
import org.blackaddons.api.world.Vec3d
import org.blackaddons.core.actions.*
import org.blackaddons.core.actions.handlers.*
import org.blackaddons.core.actions.steps.*
import org.blackaddons.core.log.Log
import java.util.ArrayDeque

internal data class QueuedAction(
    val step: ActionStep,
    var delayTicks: Int,
    val groups: Array<String>?,
    val waypoint: WaypointDefinition?,
    var elapsedTicks: Int = 0,
    // Signal number when the step got to the front. A WAIT only counts the signals after it.
    var armedAt: Long = -1,
    // The run and the position, so a block can find its END and STOP can find its run.
    val chain: List<ActionStep> = emptyList(),
    val pos: Int = 0,
    // Set on the END of a REPEAT: the loop it closes.
    var loop: LoopFrame? = null,
    val player: String? = null,
)

internal class LoopFrame(val repeat: RepeatStep.Params, val start: Int, val end: Int, var remaining: Int, var iterations: Int = 1)

private class RunningStep(val step: ActionStep, var ticksLeft: Int)

internal data class ActionExecutionStatus(
    val activeType: StepType<*>?,
    val queuedSteps: Int,
    val busy: Boolean,
)

object ActionExecutor {
    private const val MAX_LOOP_ITERATIONS = 10_000

    private val queue = ArrayDeque<QueuedAction>()

    // All running steps, so "wait for previous" doesn't forget a long step.
    private val running = ArrayList<RunningStep>()
    private var activeStep: ActionStep? = null

    internal fun newStep(type: StepType<*>): ActionStep = ActionStep.of(type)

    fun availableTypes(): List<StepType<*>> = StepTypes.all

    /** Called before addons load, so their code can use [ActionQueue] from the start. */
    fun bindQueue() = ActionQueue.bind(object : ActionQueue.Provider {
        override fun cancel() = ActionExecutor.cancel()
    })

    /** Called after step types are discovered, because loading the profiles resolves them. */
    fun init() {
        ActionStorage.load()
        ChatActionHandler.init()
        WaypointActionHandler.init()
        ShortcutActionHandler.init()
        LocationActionHandler.init()
        TempWaypoints.init()
        ActionSignals.init()
        EventBus.subscribe<ClientTickEvent.Post> { onTick() }
        EventBus.subscribe<GuiEvent.Open> { cancel() }
        EventBus.subscribe<WorldChangeEvent> {
            clear()
            ActionHudTimer.reset()
            ActionTextOverlay.reset()
        }
    }

    internal fun execute(
        steps: List<ActionStep>,
        groups: Array<String>? = null,
        waypoint: WaypointDefinition? = null,
        clearPrevious: Boolean = true,
        player: String? = null,
    ) {
        if (clearPrevious) {
            clear()
        }
        val chain = steps.filter { it.runsFor(player) }
        for (pos in chain.indices) queue.add(queued(chain, pos, groups, waypoint, player))
        armHead()
    }

    fun executeAll(actionLists: Iterable<List<ActionStep>>) {
        var clearPrevious = true
        for (steps in actionLists) {
            execute(steps, clearPrevious = clearPrevious)
            clearPrevious = false
        }
    }

    fun clear() {
        queue.clear()
        running.clear()
        finishActiveStep()
    }

    fun cancel() {
        clear()
        EventBus.post(ActionStepEvent.Cancelled)
    }

    internal fun status(): ActionExecutionStatus {
        val activeBusy = isBusy()
        return ActionExecutionStatus(
            activeType = activeStep?.type?.takeIf { activeBusy },
            queuedSteps = queue.size,
            busy = activeBusy || queue.isNotEmpty(),
        )
    }

    fun isBusy(): Boolean = running.any(::stillRunning)

    // Any step type with ongoing work holds every running step, not just steps of its own type.
    private fun stillRunning(running: RunningStep): Boolean =
        running.ticksLeft > 0 || StepTypes.all.any { it.isBusy() }

    private fun onTick() {
        if (!Platform.player.isInGame) {
            clear()
            return
        }

        tickQueue()
    }

    internal fun tickQueue() {
        running.forEach { if (it.ticksLeft > 0) it.ticksLeft-- }
        running.removeAll { !stillRunning(it) }

        val currentlyBusy = isBusy()

        if (queue.isEmpty()) {
            if (!currentlyBusy) finishActiveStep()
            return
        }

        while (queue.isNotEmpty()) {
            val qa = queue.first()
            if (currentlyBusy && qa.step.waitForPrevious) {
                break
            }
            if (qa.delayTicks > 0) {
                qa.delayTicks--
                break
            }

            val wait = qa.step.paramsOf(WaitStep)
            if (wait != null && wait.waitMode.isCondition) {
                when (waitOutcome(qa, wait)) {
                    WaitOutcome.WAITING -> break
                    WaitOutcome.MET -> {}
                    WaitOutcome.TIMED_OUT -> if (wait.stopOnTimeout) {
                        Log.main.debug("Action wait for {} timed out - stopping the remaining steps", wait.waitMode)
                        stopChain(qa)
                        continue
                    }
                }
            }

            if (qa.step.type is ControlStep) {
                queue.removeFirst()
                val looped = runControl(qa)
                armHead()
                // Max one loop pass per tick, so an empty loop can't freeze the game.
                if (looped) break
                continue
            }

            queue.removeFirst()
            armHead()
            running += RunningStep(qa.step, runStep(qa))

            if (isBusy() && queue.isNotEmpty() && queue.first().step.waitForPrevious) {
                break
            }
        }

        if (queue.isEmpty() && !isBusy()) finishActiveStep()
    }

    private fun queued(chain: List<ActionStep>, pos: Int, groups: Array<String>?, waypoint: WaypointDefinition?, player: String?): QueuedAction {
        val step = chain[pos]
        val delay = if (step.type === WaitStep) 0 else step.delayTicks
        return QueuedAction(step, delay, groups, waypoint, chain = chain, pos = pos, player = player)
    }

    /** True when a loop starts again. */
    private fun runControl(qa: QueuedAction): Boolean {
        val step = qa.step
        when (step.type) {
            StopStepsStep -> stopChain(qa)
            IfStep -> if (!ActionConditions.met(step.paramsOf(IfStep)?.condition)) {
                val otherwise = ActionBlocks.elseOf(qa.chain, qa.pos)
                if (otherwise != null) skip(qa, otherwise) else skipBlock(qa)
            }
            // Only reached after the If's own steps ran. An ELSE outside an If does nothing.
            ElseStep -> if (ActionBlocks.openerOf(qa.chain, qa.pos)?.let { qa.chain[it].type } === IfStep) skipBlock(qa)
            RepeatStep -> {
                val repeat = step.paramsOf(RepeatStep) ?: return false
                val enter = if (repeat.repeatWhile) ActionConditions.met(repeat.condition) else repeat.repeatCount > 0
                if (!enter) {
                    skipBlock(qa)
                    return false
                }
                val end = ActionBlocks.endOf(qa.chain, qa.pos)
                // An unclosed block runs once.
                queue.firstOrNull { it.chain === qa.chain && it.pos == end }?.loop =
                    LoopFrame(repeat, qa.pos, end, remaining = repeat.repeatCount - 1)
            }
            EndStep -> {
                val frame = qa.loop ?: return false
                if (!goesAgain(frame)) return false
                val again = (frame.start + 1..frame.end).map { queued(qa.chain, it, qa.groups, qa.waypoint, qa.player) }
                again.last().loop = frame
                again.asReversed().forEach(queue::addFirst)
                return true
            }
            else -> {}
        }
        return false
    }

    private fun goesAgain(frame: LoopFrame): Boolean {
        if (frame.repeat.repeatWhile) {
            if (frame.iterations >= MAX_LOOP_ITERATIONS) {
                Log.main.warn("Action loop stopped after {} passes", MAX_LOOP_ITERATIONS)
                return false
            }
            if (!ActionConditions.met(frame.repeat.condition)) return false
        } else {
            if (frame.remaining <= 0) return false
            frame.remaining--
        }
        frame.iterations++
        return true
    }

    private fun skipBlock(from: QueuedAction) = skip(from, ActionBlocks.endOf(from.chain, from.pos))

    private fun skip(from: QueuedAction, through: Int) {
        queue.removeIf { it.chain === from.chain && it.pos in from.pos + 1..through }
    }

    // Stops only this run. Steps from other triggers keep going.
    private fun stopChain(step: QueuedAction) {
        queue.removeIf { it.chain === step.chain }
    }

    private enum class WaitOutcome { WAITING, MET, TIMED_OUT }

    private fun waitOutcome(qa: QueuedAction, step: WaitStep.Params): WaitOutcome {
        val met = when (step.waitMode) {
            WaitMode.POSITION -> {
                val radius = if (step.waitRadius > 0.0) step.waitRadius else 1.0
                val dx = Platform.player.x - step.targetX
                val dy = Platform.player.y - step.targetY
                val dz = Platform.player.z - step.targetZ
                dx * dx + dy * dy + dz * dz <= radius * radius
            }
            else -> ActionSignals.matchedSince(qa.armedAt, step)
        }
        if (met) return WaitOutcome.MET
        val timeoutTicks = step.waitTimeoutSeconds.secondsToTicks
        if (timeoutTicks > 0 && qa.elapsedTicks >= timeoutTicks) return WaitOutcome.TIMED_OUT
        qa.elapsedTicks++
        return WaitOutcome.WAITING
    }

    // Arms when all earlier steps have started, so a chat line during the previous step still counts.
    private fun armHead() {
        val head = queue.peekFirst() ?: return
        if (head.armedAt < 0) head.armedAt = ActionSignals.lastSeq
    }

    /**
     * A type that throws (maybe an addon's) only loses its own step. An escaped exception skips the queue for this
     * tick, and EventBus can unregister this listener. Returns the busy ticks.
     */
    private fun runStep(qa: QueuedAction): Int {
        @Suppress("UNCHECKED_CAST")
        val type = qa.step.type as StepType<Any>? ?: run {
            Log.main.debug("Skipping action step {} - no installed addon has that step type", qa.step.typeId)
            return 0
        }
        activeStep = qa.step
        EventBus.post(ActionStepEvent.Started(type, qa.step.flagIds))
        val params = qa.step.params
        return try {
            type.execute(params, QueuedContext(qa))
            type.busyTicks(params)
        } catch (t: Throwable) {
            Log.main.error("Action step {} threw - skipping it", type.id, t)
            0
        }
    }

    private class QueuedContext(private val qa: QueuedAction) : StepContext {
        override val player: String? get() = qa.player
        override val waypoint: Vec3d? get() = qa.waypoint?.let { Vec3d(it.x, it.y, it.z) }
        override fun interpolate(text: String): String = ActionInterpolator.interpolate(text, qa.groups, qa.player)
    }

    private fun finishActiveStep() {
        if (activeStep == null) return
        activeStep = null
        EventBus.post(ActionStepEvent.Idle)
    }
}
