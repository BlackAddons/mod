package org.blackaddons.api.gui.task

import org.blackaddons.api.HostApi
import org.blackaddons.api.Platform
import org.blackaddons.api.event.*
import org.blackaddons.api.event.input.KeyInputEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.event.input.MouseScrollEvent
import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.event.world.ContainerOpenEvent
import org.blackaddons.api.util.ServerClock
import org.blackaddons.api.util.TickTimer
import org.blackaddons.api.world.ItemInfo

/**
 * Runs one [ContainerTask] at a time on the client thread. Waits count server ticks, so lag does not fail a step. A
 * task closes the menu it opened however it ends, except on a world change.
 */
object ContainerTasks {

    var active: ContainerTask? = null
        private set

    /** True while the running task holds its own menu open without a screen. */
    val holdsHiddenMenu: Boolean
        get() = active != null && hidden && ownsOpenMenu()

    /** Only hosts and tests that drive server ticks themselves replace it. */
    var clock = ServerClock()
        @HostApi set

    private var installed = false
    private var stepIndex = 0
    private var stepStarted = false
    private var deadline: ServerClock.Deadline? = null
    private var menuSeen = false
    private var menuId: Int? = null
    private var hidden = false
    private val clickDelay = TickTimer()
    private val catches = mutableListOf<Catch>()

    private class Catch(val title: Regex, val until: ServerClock.Deadline)

    private sealed interface Outcome {
        data object Wait : Outcome
        data object Next : Outcome
        class Fail(val reason: String) : Outcome
    }

    /** Host only. */
    @HostApi
    fun install() {
        if (installed) return
        installed = true
        EventBus.subscribe<ClientTickEvent.Pre> { tick() }
        EventBus.subscribe<ServerTickEvent> {
            clock.tick()
            catches.removeAll { clock.passed(it.until) }
        }
        EventBus.subscribe<ContainerOpenEvent>(EventPriority.HIGH) { onOpen(event) }
        EventBus.subscribe<WorldChangeEvent> {
            catches.clear()
            finish(ContainerTaskResult.Cancelled, cleanup = false)
        }
        EventBus.subscribe<MovementInputEvent>(EventPriority.LOWEST) {
            if (holdsHiddenMenu && active?.options?.stopMovement == true) event.release()
        }
        EventBus.subscribe<KeyInputEvent> { if (!event.synthetic && !event.isRepeat) onInput() }
        EventBus.subscribe<MouseClickEvent> { onInput() }
        EventBus.subscribe<MouseScrollEvent> { onInput() }
    }

    internal fun run(task: ContainerTask): Boolean {
        if (active === task) return true
        if (task.isFinished) return false
        if (active != null) {
            task.finish(ContainerTaskResult.Busy)
            return false
        }
        if (!Platform.player.isInGame) {
            task.finish(ContainerTaskResult.Cancelled)
            return false
        }
        catches.clear()
        resetSteps()
        active = task
        return true
    }

    internal fun cancel(task: ContainerTask) {
        if (active === task) finish(ContainerTaskResult.Cancelled) else task.finish(ContainerTaskResult.Cancelled)
    }

    private fun tick() {
        clickDelay.tick()
        val task = active ?: return
        if (!Platform.player.isInGame) return finish(ContainerTaskResult.Cancelled, cleanup = false)
        while (active === task) {
            val step = task.steps.getOrNull(stepIndex) ?: return finish(ContainerTaskResult.Success)
            when (val outcome = advance(task, step)) {
                Outcome.Wait -> return
                is Outcome.Fail -> return finish(ContainerTaskResult.Failure(outcome.reason))
                Outcome.Next -> {
                    task.completedSteps++
                    stepIndex++
                    stepStarted = false
                }
            }
        }
    }

    private fun advance(task: ContainerTask, step: ContainerStep): Outcome = when (step) {
        is ContainerStep.Open -> open(task, step)
        is ContainerStep.Click -> click(task, step)
        is ContainerStep.CatchReopen -> {
            catches += Catch(step.title, clock.after(CATCH_TIMEOUT_TICKS))
            Outcome.Next
        }
    }

    private fun open(task: ContainerTask, step: ContainerStep.Open): Outcome {
        if (!stepStarted) {
            stepStarted = true
            menuSeen = false
            deadline = clock.after(STEP_TIMEOUT_TICKS)
            Platform.chat.sendCommand(step.command)
            return Outcome.Wait
        }
        val id = Platform.container.interactionId()
        if (menuSeen && id != null && id != PLAYER_MENU_ID && menuLoaded()) {
            menuId = id
            clickDelay.arm(task.options.clickDelay)
            return Outcome.Next
        }
        return timedOut("${step.command} did not open its menu")
    }

    private fun click(task: ContainerTask, step: ContainerStep.Click): Outcome {
        if (!stepStarted) {
            stepStarted = true
            deadline = clock.after(STEP_TIMEOUT_TICKS)
        }
        if (!ownsOpenMenu()) return Outcome.Fail("The menu closed")
        if (!clickDelay.ready) return Outcome.Wait
        val (slot, item) = find(step) ?: return timedOut(step.failure)
        if (!step.skipIf(item)) {
            Platform.container.clickSlot(slot, button = 0, quickMove = step.quickMove)
            clickDelay.arm(task.options.clickDelay)
        }
        return Outcome.Next
    }

    private fun timedOut(reason: String): Outcome =
        if (deadline?.let(clock::passed) == true) Outcome.Fail(reason) else Outcome.Wait

    private fun find(step: ContainerStep.Click): Pair<Int, ItemInfo>? {
        if (step.inChest) {
            Platform.container.capture()?.raw?.forEachIndexed { slot, raw ->
                val item = raw?.let(Platform.items::describe) ?: return@forEachIndexed
                if (step.match(item)) return slot to item
            }
            return null
        }
        val inventory = Platform.container.capturePlayerInventory() ?: return null
        inventory.raw.forEachIndexed { index, raw ->
            val item = raw?.let(Platform.items::describe) ?: return@forEachIndexed
            if (step.match(item)) return inventory.menuSlots[index] to item
        }
        return null
    }

    private fun menuLoaded(): Boolean = Platform.container.capture()?.raw?.any { it != null } == true

    private fun ownsOpenMenu(): Boolean = menuId != null && Platform.container.interactionId() == menuId

    private fun onOpen(event: ContainerOpenEvent) {
        val caught = catches.firstOrNull { it.title.matches(event.title) }
        if (caught != null) {
            catches.remove(caught)
            event.cancelled = true
            return
        }
        val task = active ?: return
        val step = task.steps.getOrNull(stepIndex) as? ContainerStep.Open ?: return
        if (!stepStarted || menuSeen || !step.title.matches(event.title)) return
        menuSeen = true
        hidden = task.options.silent
        event.headless = hidden
    }

    private fun onInput() {
        val task = active ?: return
        if (task.options.cancelOnInput && holdsHiddenMenu) finish(ContainerTaskResult.Cancelled)
    }

    private fun finish(result: ContainerTaskResult, cleanup: Boolean = true) {
        val task = active ?: return
        if (cleanup && ownsOpenMenu()) Platform.container.close()
        if (result != ContainerTaskResult.Success) catches.clear()
        active = null
        resetSteps()
        task.finish(result)
    }

    private fun resetSteps() {
        stepIndex = 0
        stepStarted = false
        deadline = null
        menuSeen = false
        menuId = null
        hidden = false
        clickDelay.clear()
    }

    private const val STEP_TIMEOUT_TICKS = 20
    private const val CATCH_TIMEOUT_TICKS = 20
    private const val PLAYER_MENU_ID = 0
}
