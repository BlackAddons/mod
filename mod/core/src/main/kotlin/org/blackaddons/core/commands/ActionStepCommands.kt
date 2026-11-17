package org.blackaddons.core.commands

import org.blackaddons.api.commands.ArgType
import org.blackaddons.api.commands.CommandBuilder
import org.blackaddons.api.commands.CommandContext
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.execution.ActionRunner
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.typeName

internal fun <T> CommandBuilder.registerStepBranches(
    collection: ActionCollection<T>,
    readSteps: (T) -> List<ActionStep>,
    ensureSteps: (T) -> MutableList<ActionStep>,
) {
    subcommand("list") {
        targetArgument(collection, finalArgument = true) { item -> listSteps(collection, item, readSteps(item)) }
    }
    subcommand("create") {
        targetArgument(collection, finalArgument = true) { item ->
            openLater { ActionEditors.createStep(ensureSteps(item), collection.save) }
        }
    }
    subcommand("edit") {
        stepArgument(collection, ensureSteps) { _, step, _ ->
            openLater { ActionEditors.editStep(step, collection.save) }
        }
    }
    subcommand("run") {
        stepArgument(collection, ensureSteps) { item, step, _ ->
            if (ActionRunner.run(listOf(step), waypoint = collection.waypoint(item))) {
                replyAction(commandTr("queued", "Queued %s.", step.typeName), Brand.OK)
            } else {
                replyAction(commandTr("step_disabled", "That step is disabled."), Brand.FAIL)
            }
        }
    }
    registerStepEnabledBranch("enable", collection, ensureSteps) { true }
    registerStepEnabledBranch("disable", collection, ensureSteps) { false }
    registerStepEnabledBranch("toggle", collection, ensureSteps) { !it.enabled }
    subcommand("move") {
        arg("target", ArgType.StrArg) {
            suggests { collection.suggestions(finalArgument = false) }
            arg("step", ArgType.IntArg(min = 1)) {
                arg("position", ArgType.IntArg(min = 1)) {
                    execute {
                        val item = resolve(collection, str("target")) ?: return@execute
                        val steps = ensureSteps(item)
                        val step = steps.getOrNull(int("step") - 1) ?: run {
                            replyAction(commandTr("step_not_found", "Step %d was not found.", int("step")), Brand.FAIL)
                            return@execute
                        }
                        val position = moveTo(steps, step, int("position"))
                        collection.save()
                        replyAction(commandTr("step_moved", "Moved the step to position %d.", position), Brand.OK)
                    }
                }
            }
        }
    }
    subcommand("delete") {
        arg("target", ArgType.StrArg) {
            suggests { collection.suggestions(finalArgument = false) }
            arg("step", ArgType.IntArg(min = 1)) {
                subcommand("confirm") {
                    execute {
                        val item = resolve(collection, str("target")) ?: return@execute
                        val steps = ensureSteps(item)
                        val step = steps.getOrNull(int("step") - 1) ?: run {
                            replyAction(commandTr("step_not_found", "Step %d was not found.", int("step")), Brand.FAIL)
                            return@execute
                        }
                        if (steps.removeIdentity(step)) {
                            collection.save()
                            replyAction(commandTr("deleted", "Deleted %s.", step.typeName), Brand.OK)
                        } else {
                            replyAction(commandTr("step_gone", "The step no longer exists."), Brand.FAIL)
                        }
                    }
                }
            }
        }
    }
}

private fun <T> CommandBuilder.registerStepEnabledBranch(
    command: String,
    collection: ActionCollection<T>,
    ensureSteps: (T) -> MutableList<ActionStep>,
    value: (ActionStep) -> Boolean,
) {
    subcommand(command) {
        stepArgument(collection, ensureSteps) { _, step, _ ->
            step.enabled = value(step)
            collection.save()
            replyAction(
                commandTr(
                    "enabled_state", "%s is now %s.", step.typeName,
                    commandTr(if (step.enabled) "enabled" else "disabled", if (step.enabled) "enabled" else "disabled"),
                ),
                Brand.OK,
            )
        }
    }
}

private fun <T> CommandBuilder.stepArgument(
    collection: ActionCollection<T>,
    ensureSteps: (T) -> MutableList<ActionStep>,
    action: CommandContext.(T, ActionStep, MutableList<ActionStep>) -> Unit,
) {
    arg("target", ArgType.StrArg) {
        suggests { collection.suggestions(finalArgument = false) }
        arg("step", ArgType.IntArg(min = 1)) {
            execute {
                val item = resolve(collection, str("target")) ?: return@execute
                val steps = ensureSteps(item)
                val step = steps.getOrNull(int("step") - 1) ?: run {
                    replyAction(commandTr("step_not_found", "Step %d was not found.", int("step")), Brand.FAIL)
                    return@execute
                }
                action(this, item, step, steps)
            }
        }
    }
}
