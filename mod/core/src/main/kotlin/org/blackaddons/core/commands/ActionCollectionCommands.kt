package org.blackaddons.core.commands

import org.blackaddons.api.commands.ArgType
import org.blackaddons.api.commands.CommandBuilder
import org.blackaddons.api.commands.CommandContext
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.WaypointDefinition
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.ActionsPage

internal class ActionCollection<T>(
    val path: String,
    val title: String,
    val page: ActionsPage,
    val items: () -> MutableList<T>,
    val name: (T) -> String,
    val id: (T) -> String? = { null },
    val details: (T) -> String,
    val enabled: (T) -> Boolean,
    val setEnabled: (T, Boolean) -> Unit,
    val create: CommandContext.() -> Unit,
    val edit: CommandContext.(T) -> Unit,
    val save: () -> Unit,
    val delete: (T) -> Boolean,
    val steps: ((T) -> MutableList<ActionStep>?)? = null,
    val ensureSteps: ((T) -> MutableList<ActionStep>)? = null,
    val run: ((T) -> Boolean)? = null,
    val waypoint: ((T) -> WaypointDefinition?) = { null },
) {
    fun reference(item: T): String {
        val index = items().indexOfFirst { it === item }
        return id(item) ?: (index + 1).toString()
    }

    fun suggestions(finalArgument: Boolean): List<String> = buildList {
        items().forEachIndexed { index, item ->
            add((index + 1).toString())
            id(item)?.let(::add)
            if (finalArgument) name(item).takeIf(String::isNotBlank)?.let(::add)
        }
    }.distinct()
}

internal sealed interface ActionReferenceResult<out T> {
    data class Found<T>(val item: T) : ActionReferenceResult<T>
    data object NotFound : ActionReferenceResult<Nothing>
    data object Ambiguous : ActionReferenceResult<Nothing>
}

internal fun <T> resolveActionReference(
    items: List<T>,
    reference: String,
    id: (T) -> String?,
    name: (T) -> String,
): ActionReferenceResult<T> {
    val trimmed = reference.trim()
    trimmed.toIntOrNull()?.let { index ->
        items.getOrNull(index - 1)?.let { return ActionReferenceResult.Found(it) }
    }
    items.firstOrNull { id(it)?.equals(trimmed, ignoreCase = true) == true }
        ?.let { return ActionReferenceResult.Found(it) }
    val named = items.filter { name(it).equals(trimmed, ignoreCase = true) }
    return when (named.size) {
        1 -> ActionReferenceResult.Found(named.first())
        0 -> ActionReferenceResult.NotFound
        else -> ActionReferenceResult.Ambiguous
    }
}

internal fun <T> CommandBuilder.registerCollection(collection: ActionCollection<T>) {
    execute { openLater { ActionEditors.open(collection.page) } }

    subcommand("list") { execute { listActions(collection) } }
    subcommand("create") { execute { collection.create(this) } }
    subcommand("info") {
        targetArgument(collection, finalArgument = true) { item ->
            replyAction("${collection.name(item)}: ${collection.details(item)}")
        }
    }
    subcommand("edit") {
        targetArgument(collection, finalArgument = true) { item -> collection.edit(this, item) }
    }
    registerEnabledBranch("enable", collection) { true }
    registerEnabledBranch("disable", collection) { false }
    registerEnabledBranch("toggle", collection) { item -> !collection.enabled(item) }

    collection.run?.let { runner ->
        subcommand("run") {
            targetArgument(collection, finalArgument = true) { item ->
                if (runner(item)) {
                    replyAction(commandTr("queued", "Queued %s.", collection.name(item)), Brand.OK)
                } else {
                    replyAction(commandTr("no_enabled_steps", "%s has no enabled steps.", collection.name(item)), Brand.FAIL)
                }
            }
        }
    }

    val ensureSteps = collection.ensureSteps
    if (ensureSteps != null) {
        subcommand("steps") {
            targetArgument(collection, finalArgument = true) { item ->
                openLater { ActionEditors.openSteps(ensureSteps(item), persist = collection.save) }
            }
        }
        val readSteps: (T) -> List<ActionStep> = { item -> collection.steps?.invoke(item).orEmpty() }
        subcommand("step") { registerStepBranches(collection, readSteps, ensureSteps) }
    }

    subcommand("move") {
        arg("target", ArgType.StrArg) {
            suggests { collection.suggestions(finalArgument = false) }
            arg("position", ArgType.IntArg(min = 1)) {
                execute {
                    val item = resolve(collection, str("target")) ?: return@execute
                    val position = moveTo(collection.items(), item, int("position"))
                    collection.save()
                    replyAction(commandTr("moved", "Moved %s to position %d.", collection.name(item), position), Brand.OK)
                }
            }
        }
    }
    subcommand("delete") {
        arg("target", ArgType.StrArg) {
            suggests { collection.suggestions(finalArgument = false) }
            subcommand("confirm") {
                execute {
                    val item = resolve(collection, str("target")) ?: return@execute
                    if (collection.delete(item)) {
                        replyAction(commandTr("deleted", "Deleted %s.", collection.name(item)), Brand.OK)
                    } else {
                        replyAction(commandTr("target_gone", "The target no longer exists."), Brand.FAIL)
                    }
                }
            }
        }
    }
}

private fun <T> CommandBuilder.registerEnabledBranch(
    command: String,
    collection: ActionCollection<T>,
    value: (T) -> Boolean,
) {
    subcommand(command) {
        targetArgument(collection, finalArgument = true) { item ->
            val enabled = value(item)
            collection.setEnabled(item, enabled)
            collection.save()
            replyAction(
                commandTr(
                    "enabled_state", "%s is now %s.", collection.name(item),
                    commandTr(if (enabled) "enabled" else "disabled", if (enabled) "enabled" else "disabled"),
                ),
                Brand.OK,
            )
        }
    }
}

internal fun <T> CommandBuilder.targetArgument(
    collection: ActionCollection<T>,
    finalArgument: Boolean,
    action: CommandContext.(T) -> Unit,
) {
    arg("target", if (finalArgument) ArgType.GreedyStrArg else ArgType.StrArg) {
        suggests { collection.suggestions(finalArgument) }
        execute {
            val item = resolve(collection, str("target")) ?: return@execute
            action(this, item)
        }
    }
}

internal fun <T> CommandContext.resolve(collection: ActionCollection<T>, raw: String): T? {
    val reference = raw.trim()
    return when (val result = resolveActionReference(collection.items(), reference, collection.id, collection.name)) {
        is ActionReferenceResult.Found -> result.item
        ActionReferenceResult.NotFound -> null.also {
            replyAction(commandTr("target_not_found", "%s target '%s' was not found.", collection.title, reference), Brand.FAIL)
        }
        ActionReferenceResult.Ambiguous -> null.also {
            replyAction(commandTr("ambiguous", "'%s' is ambiguous; use its index or ID.", reference), Brand.FAIL)
        }
    }
}

internal fun <T> moveTo(items: MutableList<T>, item: T, oneBasedPosition: Int): Int {
    val from = items.indexOfFirst { it === item }
    if (from < 0) return -1
    items.removeAt(from)
    val index = (oneBasedPosition - 1).coerceIn(0, items.size)
    items.add(index, item)
    return index + 1
}

internal fun <T> MutableList<T>.removeIdentity(target: T): Boolean {
    val index = indexOfFirst { it === target }
    if (index < 0) return false
    removeAt(index)
    return true
}
