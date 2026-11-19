package org.blackaddons.cheat.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.actions.NoParams
import org.blackaddons.api.actions.StepCategory
import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.actions.StepFlag
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.ui.node.NodeProperty
import org.blackaddons.api.ui.node.nodeProperties
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.api.util.secondsToTicks

// Same keys as the core's step texts, so the editor reads as one thing. This addon's bundle carries them.
private val tr = I18n.scope("blackaddons.actions")

private fun prop(key: String, fallback: String): String = tr("graph.prop.$key", fallback)

/**
 * Keeps the camera silent from this step to the next one. The key is older than step flags, so it stays exactly as it
 * was saved.
 */
object NoRotateFlag : StepFlag("noRotateUntilNextStep") {
    override val label: String get() = prop("no_rotate", "No rotate until next")

    // Align keeps its own camera.
    override fun appliesTo(type: StepType<*>): Boolean = super.appliesTo(type) && type !== AlignStep
}

/**
 * The cheat steps share one set of engines, and while any of them runs, every step with "wait for previous" waits too.
 */
sealed class CheatStep<P : Any>(id: String) : StepType<P>(id) {
    override fun isBusy(): Boolean = CheatActionStepHandler.isBusy
}

/** Steps that are held for a time: the step counts as running for its whole duration. */
sealed class HeldStep(id: String) : CheatStep<HeldStep.Params>(id) {
    data class Params(var durationSeconds: Float = 0f) {
        val durationTicks: Int get() = durationSeconds.secondsToTicks
    }

    override val category: StepCategory get() = StepCategory.ITEM

    override fun newParams() = Params()

    override fun busyTicks(params: Params): Int = params.durationTicks

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        number(prop("hold_for", "Hold for"), params::durationSeconds, 0.05, min = 0.0)
    }
}

object UseItemStep : HeldStep("USE_ITEM") {
    override val displayName: String get() = tr("step_type.use_item", "Use Item")

    override fun execute(params: Params, context: StepContext) = CheatActionStepHandler.useItem(params.durationTicks)
}

object AttackStep : HeldStep("ATTACK") {
    override val displayName: String get() = tr("step_type.attack", "Attack / Mine")

    override fun execute(params: Params, context: StepContext) = CheatActionStepHandler.attack(params.durationTicks)
}

object SwitchSlotStep : CheatStep<SwitchSlotStep.Params>("SWITCH_SLOT") {
    data class Params(var slotIndex: Int = 0)

    private const val HOTBAR = 9

    override val displayName: String get() = tr("step_type.switch_slot", "Switch Slot")
    override val category: StepCategory get() = StepCategory.ITEM

    override fun newParams() = Params()

    override fun execute(params: Params, context: StepContext) {
        if (params.slotIndex in 0 until HOTBAR) Platform.interaction.selectHotbarSlot(params.slotIndex)
    }

    override fun summary(params: Params): String = tr("steps.slot", "Slot %d", params.slotIndex + 1)

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        val slots = (1..HOTBAR).map { tr("steps.slot", "Slot %d", it) }
        choice(prop("slot", "Slot"), slots, { params.slotIndex.coerceIn(0, HOTBAR - 1) }) { params.slotIndex = it }
    }
}

object MoveKeybindsStep : CheatStep<MoveKeybindsStep.Params>("MOVE_KEYBINDS") {
    data class Params(var movementKeybinds: List<String> = emptyList(), var durationSeconds: Float = 0f) {
        val durationTicks: Int get() = durationSeconds.secondsToTicks
    }

    override val displayName: String get() = tr("step_type.move_keybinds", "Move Keybinds")
    override val category: StepCategory get() = StepCategory.MOVEMENT

    override fun newParams() = Params()

    override fun busyTicks(params: Params): Int = params.durationTicks

    override fun execute(params: Params, context: StepContext) =
        CheatActionStepHandler.holdMovement(params.movementKeybinds, params.durationTicks)

    override fun summary(params: Params): String = params.movementKeybinds.joinToString(", ")

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        number(prop("duration", "Duration"), params::durationSeconds, 0.05, min = 0.0)
        movementKeys(prop("keys", "Keys"), params::movementKeybinds)
    }
}

object PressKeybindStep : CheatStep<PressKeybindStep.Params>("PRESS_KEYBIND") {
    /** [message] is the key name, for example `key.keyboard.f5`. */
    data class Params(var message: String = "", var durationSeconds: Float = 0f) {
        val durationTicks: Int get() = durationSeconds.secondsToTicks
    }

    override val displayName: String get() = tr("step_type.press_keybind", "Press Keybind")

    override fun newParams() = Params()

    override fun busyTicks(params: Params): Int = params.durationTicks

    override fun execute(params: Params, context: StepContext) = CheatActionStepHandler.pressKey(params.message, params.durationTicks)

    override fun summary(params: Params): String = params.message

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        number(prop("duration", "Duration"), params::durationSeconds, 0.05, min = 0.0)
        text(
            prop("keybind", "Keybind"), params::message, "f5",
            suggestions = { query ->
                val names = Platform.keyNames.allKeyNames()
                if (query.isEmpty()) names else names.filter { it.containsIgnoreCase(query) }
            },
            suggestionDisplay = { it.removePrefix("key.keyboard.") },
        )
    }
}

object RotateStep : CheatStep<RotateStep.Params>("ROTATE") {
    data class Params(
        var useCoordinates: Boolean = false,
        var targetX: Double = 0.0,
        var targetY: Double = 0.0,
        var targetZ: Double = 0.0,
        var yaw: Float = 0f,
        var pitch: Float = 0f,
        var aimSpeed: Float = 1f, // Replaced rotationSpeed in 1.0.1
        var lookAtSeconds: Float = 0f,
        var instaSnap: Boolean = false,
    )

    override val displayName: String get() = tr("step_type.rotate", "Rotate Camera")
    override val category: StepCategory get() = StepCategory.MOVEMENT

    override fun newParams() = Params()

    override fun execute(params: Params, context: StepContext) = CheatActionStepHandler.rotate(params)

    override fun summary(params: Params): String =
        if (params.useCoordinates) "X:${params.targetX.toInt()} Y:${params.targetY.toInt()} Z:${params.targetZ.toInt()}"
        else "Yaw:${params.yaw.toInt()} Pitch:${params.pitch.toInt()}"

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        toggle(prop("use_coordinates", "Use coordinates"), params::useCoordinates)
        if (params.useCoordinates) coordinates(params::targetX, params::targetY, params::targetZ)
        else rotation(params::yaw, params::pitch)
        number(prop("speed", "Speed"), params::aimSpeed, 0.1, min = 0.1, max = 5.0, decimals = 1)
        number(prop("look_time", "Look time"), params::lookAtSeconds, 0.1, min = 0.0, decimals = 1)
        toggle(prop("instant_snap", "Instant snap"), params::instaSnap)
    }
}

object AlignStep : CheatStep<AlignStep.Params>("ALIGN") {
    data class Params(
        var useCoordinates: Boolean = false,
        var targetX: Double = 0.0,
        var targetY: Double = 0.0,
        var targetZ: Double = 0.0,
        /** Timeout. 0 means use the Align setting. */
        var durationSeconds: Float = 0f,
        var lookAfterAlign: Boolean = false,
        var alignPostYaw: Float = 0f,
        var alignPostPitch: Float = 0f,
        var useLookAfterCoords: Boolean = false,
        var alignLookAtX: Double = 0.0,
        var alignLookAtY: Double = 0.0,
        var alignLookAtZ: Double = 0.0,
        /** Null means use the Align setting. */
        var dontWaitForPerfectAlign: Boolean? = null,
    )

    override val displayName: String get() = tr("step_type.align", "Align")
    override val category: StepCategory get() = StepCategory.MOVEMENT

    override fun newParams() = Params(dontWaitForPerfectAlign = AlignSettings.mayFinishWhileMoving)

    override fun execute(params: Params, context: StepContext) = CheatActionStepHandler.align(params, context)

    override fun summary(params: Params): String = "X:${params.targetX.toInt()} Z:${params.targetZ.toInt()}"

    override fun properties(params: Params): List<NodeProperty> = nodeProperties {
        toggle(prop("use_coordinates", "Use coordinates"), params::useCoordinates)
        if (params.useCoordinates) coordinates(params::targetX, null, params::targetZ)
        toggle(prop("perfect_align", "Don't wait for perfect align"), { params.dontWaitForPerfectAlign ?: false }) { params.dontWaitForPerfectAlign = it }
        toggle(prop("look_after", "Look after align"), params::lookAfterAlign)
        if (!params.lookAfterAlign) return@nodeProperties
        toggle(prop("look_coordinates", "Look at coordinates"), params::useLookAfterCoords)
        if (params.useLookAfterCoords) coordinates(params::alignLookAtX, params::alignLookAtY, params::alignLookAtZ)
        else rotation(params::alignPostYaw, params::alignPostPitch)
    }
}

object StopInertiaStep : CheatStep<NoParams>("STOP") {
    override val displayName: String get() = tr("step_type.stop", "Stop Inertia")
    override val category: StepCategory get() = StepCategory.MOVEMENT

    override fun newParams() = NoParams()

    override fun execute(params: NoParams, context: StepContext) = CheatActionStepHandler.stopInertia()

    override fun summary(params: NoParams): String = tr("steps.stop_inertia", "Stop Inertia")

    override fun properties(params: NoParams): List<NodeProperty> = nodeProperties {
        note(tr("step.stop_help", "Cancels player movement inertia."))
    }
}
