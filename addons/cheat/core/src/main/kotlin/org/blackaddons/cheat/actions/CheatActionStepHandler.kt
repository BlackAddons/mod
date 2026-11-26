package org.blackaddons.cheat.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.actions.ActionQueue
import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.event.*
import org.blackaddons.api.event.actions.ActionStepEvent
import org.blackaddons.api.event.input.ItemUseMovementChangeEvent
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.event.world.BlockInteractEvent
import org.blackaddons.api.event.world.InventoryEvent
import org.blackaddons.api.player.*
import org.blackaddons.api.world.Vec3d
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

/** State and engines behind the cheat steps ([CheatStep]). */
object CheatActionStepHandler {
    private val activeMovementKeys = ConcurrentHashMap<String, Int>()
    private var attackHeldTicks = 0
    private var useHeldTicks = 0
    private var attackHold: Lease? = null
    private var keyHeldTicks = 0
    private var keyPress: Lease? = null
    private var useHold: Lease? = null

    internal val isBusy: Boolean
        get() = AlignEngine.active || AlignEngine.shouldBlockMovementInput || StopEngine.active || ActionAim.isBusy ||
            activeMovementKeys.isNotEmpty() || attackHeldTicks > 0 || useHeldTicks > 0 || keyHeldTicks > 0

    fun init() {
        val silentRotation = ActionStepSilentRotation(
            begin = ActionAim::beginSilentSpan,
            finish = { ActionAim.endSilentSpan(HandBack.KeepView) },
            discard = { ActionAim.endSilentSpan(HandBack.Discard) },
            smoothReset = { ActionAim.endSilentSpan(HandBack.Ease(AlignSettings.yawResetSpeed)) },
            silentAlignEnabled = { !AlignSettings.isSilentAlignDisabled },
            snapVisualAfterAlign = { AlignEngine.shouldSnapVisualAfterCompletion },
            isSmoothResetEnabled = { AlignSettings.isSmoothAlignYawResetEnabled },
        )
        EventBus.subscribe<ActionStepEvent.Started> { silentRotation.onStepChanged(event.type, event.has(NoRotateFlag)) }
        EventBus.subscribe<ActionStepEvent.Idle> { silentRotation.onStepChanged(null, false) }
        EventBus.subscribe<ActionStepEvent.Cancelled> { reset() }
        EventBus.subscribe<ClientTickEvent.Pre> { onPreTick() }
        EventBus.subscribe<ClientTickEvent.Post> { onTick() }
        EventBus.subscribe<WorldChangeEvent> { reset() }
        EventBus.subscribe<ItemUseMovementChangeEvent>(EventPriority.HIGHEST) {
            if (AlignEngine.active) cancelForItemUseSpeedChange()
        }
        // Kept for the whole game: steer returns null whenever no step moves the player.
        Platform.movement.drive(this, DrivePriority.HIGH, ::steer)
        EventBus.subscribe<RenderWorldEvent> { AlignDebugger.onRenderWorld() }
        EventBus.subscribe<BlockInteractEvent>(EventPriority.HIGHEST) { if (AlignEngine.active) event.cancelled = true }
        EventBus.subscribe<InventoryEvent.SlotClick>(EventPriority.HIGHEST) {
            if (AlignEngine.active || activeMovementKeys.isNotEmpty()) event.cancelled = true
        }
    }

    internal fun useItem(ticks: Int) {
        if (ticks > 0) {
            useHeldTicks = ticks
            useHold = Platform.interaction.hold(MouseButton.USE, this)
        } else {
            Platform.interaction.rightClick()
        }
    }

    internal fun attack(ticks: Int) {
        if (ticks > 0) {
            attackHeldTicks = ticks
            attackHold = Platform.interaction.hold(MouseButton.ATTACK, this)
        } else {
            Platform.interaction.leftClick()
        }
    }

    internal fun rotate(step: RotateStep.Params) {
        val params = RotateSettings.params(speed = step.aimSpeed, humanize = !step.instaSnap)
        val aim = ActionAim.lease()
        val point = if (step.useCoordinates) Vec3d(step.targetX, step.targetY, step.targetZ) else null
        if (point != null) aim.turnToPoint(point, params) else aim.turnTo(step.yaw, step.pitch, params)
        if (step.instaSnap) aim.snap()
        ActionAim.look(step.lookAtSeconds)
    }

    internal fun holdMovement(keys: List<String>, ticks: Int) {
        val dur = ticks.coerceAtLeast(1)
        for (key in keys) {
            activeMovementKeys[key.uppercase()] = dur
        }
    }

    internal fun pressKey(name: String, ticks: Int) {
        keyPress?.release()
        keyPress = Platform.interaction.pressKey(name) ?: return
        // +1 because the countdown already runs this tick, and a polled bind has to see the key down for a whole tick.
        keyHeldTicks = ticks.coerceAtLeast(1) + 1
    }

    internal fun align(step: AlignStep.Params, context: StepContext) {
        if (Platform.movement.isItemUseChangingSpeed) {
            cancelForItemUseSpeedChange()
            return
        }
        val target = if (step.useCoordinates) Vec3d(step.targetX, 0.0, step.targetZ) else context.waypoint ?: return
        val timeoutMs = if (step.durationSeconds > 0f) (step.durationSeconds * 1000f).toLong() else AlignSettings.timeoutMs
        resetHeldInteractions()
        AlignEngine.start(
            target.x,
            target.z,
            timeoutMs,
            step.lookAfterAlign,
            step.useLookAfterCoords,
            step.alignPostYaw,
            step.alignPostPitch,
            step.alignLookAtX,
            step.alignLookAtY,
            step.alignLookAtZ,
            AlignSettings.shouldFinishWhileMoving(step.dontWaitForPerfectAlign),
        )
    }

    internal fun stopInertia() {
        resetHeldInteractions()
        StopEngine.start()
    }

    private fun onPreTick() {
        if (!Platform.player.isInGame) reset()
    }

    private fun onTick() {
        if (!Platform.player.isInGame) {
            reset()
            return
        }

        if (AlignEngine.active && Platform.movement.isItemUseChangingSpeed) cancelForItemUseSpeedChange()
        else AlignEngine.tick()
        StopEngine.tick()

        if (attackHeldTicks > 0 && --attackHeldTicks == 0) attackHold?.release()
        if (useHeldTicks > 0 && --useHeldTicks == 0) useHold?.release()
        if (keyHeldTicks > 0 && --keyHeldTicks == 0) keyPress?.release()
        ActionAim.tick()
        if (!AlignEngine.active) ActionAim.releaseIfIdle()

        activeMovementKeys.entries.removeAll { entry ->
            if (entry.value > 1) {
                entry.setValue(entry.value - 1)
                false
            } else {
                true
            }
        }
    }

    private fun steer(): Set<MoveKey>? = when {
        // Handing back eases the view to the cursor. If we walk now, we'd go somewhere the player isn't looking.
        ActionAim.isHandingBack -> emptySet()
        StopEngine.shouldBlockMovementInput -> keys(
            StopEngine.forcedForward, StopEngine.forcedBackward, StopEngine.forcedLeft, StopEngine.forcedRight,
        )
        AlignEngine.shouldBlockMovementInput -> keys(
            AlignEngine.forcedForward, AlignEngine.forcedBackward, AlignEngine.forcedLeft, AlignEngine.forcedRight,
            sneak = AlignEngine.forcedSneak,
        )
        activeMovementKeys.isNotEmpty() -> activeMovementKeys.keys.mapNotNullTo(EnumSet.noneOf(MoveKey::class.java), ::moveOf)
        else -> null
    }

    private fun keys(forward: Boolean, backward: Boolean, left: Boolean, right: Boolean, sneak: Boolean = false): Set<MoveKey> =
        EnumSet.noneOf(MoveKey::class.java).apply {
            if (forward) add(MoveKey.FORWARD)
            if (backward) add(MoveKey.BACKWARD)
            if (left) add(MoveKey.LEFT)
            if (right) add(MoveKey.RIGHT)
            if (sneak) add(MoveKey.SNEAK)
        }

    private fun resetHeldInteractions() {
        attackHeldTicks = 0
        useHeldTicks = 0
        keyHeldTicks = 0
        attackHold?.release()
        useHold?.release()
        keyPress?.release()
    }

    private fun reset() {
        activeMovementKeys.clear()
        AlignEngine.cancel()
        StopEngine.cancel()
        ActionAim.release()
        resetHeldInteractions()
    }

    private fun cancelForItemUseSpeedChange() {
        ActionQueue.cancel()
        ActionAim.release()
    }

    private fun moveOf(key: String): MoveKey? = when (key) {
        "W", "FORWARD", "KEY.KEYBOARD.W" -> MoveKey.FORWARD
        "S", "BACKWARD", "KEY.KEYBOARD.S" -> MoveKey.BACKWARD
        "A", "LEFT", "KEY.KEYBOARD.A" -> MoveKey.LEFT
        "D", "RIGHT", "KEY.KEYBOARD.D" -> MoveKey.RIGHT
        "SPACE", "JUMP", "KEY.KEYBOARD.SPACE" -> MoveKey.JUMP
        "SHIFT", "SNEAK", "KEY.KEYBOARD.LEFT.SHIFT" -> MoveKey.SNEAK
        "SPRINT", "KEY.KEYBOARD.LEFT.CONTROL" -> MoveKey.SPRINT
        else -> null
    }
}
