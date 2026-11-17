package org.blackaddons.core.commands

import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.commands.ArgType
import org.blackaddons.api.commands.CommandBuilder
import org.blackaddons.api.commands.CommandContext
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.ui.screen.actions.ActionEditors

internal fun CommandBuilder.registerProfileBranches() {
    execute { openLater { ActionEditors.manageProfiles() } }
    subcommand("manage") { execute { openLater { ActionEditors.manageProfiles() } } }
    subcommand("folder") {
        execute {
            ActionStorage.openFolder()
            replyAction(commandTr("folder_opened", "Opened the actions folder."), Brand.OK)
        }
    }
    subcommand("list") { execute { listProfiles() } }
    subcommand("select") {
        arg("profile", ArgType.GreedyStrArg) {
            suggests { profileSuggestions(finalArgument = true) }
            execute {
                val profile = resolveProfile(str("profile")) ?: return@execute
                ActionStorage.selectProfile(profile.id)
                replyAction(commandTr("profile_selected", "Selected profile %s.", profile.name), Brand.OK)
            }
        }
    }
    subcommand("create") {
        arg("name", ArgType.GreedyStrArg) {
            execute {
                val profile = ActionStorage.createProfile(str("name"), copyCurrent = false)
                replyAction(commandTr("profile_created", "Created and selected profile %s.", profile.name), Brand.OK)
            }
        }
    }
    subcommand("copy") {
        arg("name", ArgType.GreedyStrArg) {
            execute {
                val profile = ActionStorage.createProfile(str("name"), copyCurrent = true)
                replyAction(commandTr("profile_copied", "Copied the current profile to %s.", profile.name), Brand.OK)
            }
        }
    }
    subcommand("rename") {
        arg("profile", ArgType.StrArg) {
            suggests { profileSuggestions(finalArgument = false) }
            arg("name", ArgType.GreedyStrArg) {
                execute {
                    val profile = resolveProfile(str("profile")) ?: return@execute
                    ActionStorage.renameProfile(profile.id, str("name"))
                    replyAction(commandTr("profile_renamed", "Renamed the profile to %s.", profile.name), Brand.OK)
                }
            }
        }
    }
    subcommand("delete") {
        arg("profile", ArgType.StrArg) {
            suggests { profileSuggestions(finalArgument = false) }
            subcommand("confirm") {
                execute {
                    val profile = resolveProfile(str("profile")) ?: return@execute
                    if (ActionStorage.deleteProfile(profile.id)) {
                        replyAction(commandTr("profile_deleted", "Deleted profile %s.", profile.name), Brand.OK)
                    } else {
                        replyAction(commandTr("last_profile", "The last profile cannot be deleted."), Brand.FAIL)
                    }
                }
            }
        }
    }
}

private fun CommandContext.listProfiles() {
    val profiles = ActionStorage.manifest.profiles
    replyAction(commandTr("profiles", "Action Profiles (%d):", profiles.size), Theme.ACCENT)
    profiles.forEachIndexed { index, profile ->
        val active = profile.id == ActionStorage.activeProfile.id
        reply(
            ChatMessage.build {
                append("  ${index + 1}. ${if (active) "[${commandTr("active", "ACTIVE")}] " else ""}", if (active) Brand.OK else Theme.TEXT_MUTED)
                append(profile.name, Brand.BODY)
                if (!active) {
                    append(
                        " [${commandTr("select", "Select")}]",
                        Theme.ACCENT,
                        click = ChatClick.RunCommand("$ACTION_COMMAND profile select ${profile.id}"),
                    )
                }
                append(
                    " [${commandTr("delete", "Delete")}]",
                    Brand.FAIL,
                    click = ChatClick.SuggestCommand("$ACTION_COMMAND profile delete ${profile.id} confirm"),
                )
            },
        )
    }
}

private fun CommandContext.resolveProfile(raw: String) = run {
    val reference = raw.trim()
    val profiles = ActionStorage.manifest.profiles
    reference.toIntOrNull()?.let { profiles.getOrNull(it - 1) }?.let { return@run it }
    profiles.firstOrNull { it.id.equals(reference, ignoreCase = true) }?.let { return@run it }
    val named = profiles.filter { it.name.equals(reference, ignoreCase = true) }
    when (named.size) {
        1 -> named.first()
        0 -> null.also { replyAction(commandTr("profile_not_found", "Profile '%s' was not found.", reference), Brand.FAIL) }
        else -> null.also { replyAction(commandTr("ambiguous", "'%s' is ambiguous; use its index or ID.", reference), Brand.FAIL) }
    }
}

private fun profileSuggestions(finalArgument: Boolean): List<String> = buildList {
    ActionStorage.manifest.profiles.forEachIndexed { index, profile ->
        add(if (profile.id == "default") profile.id else (index + 1).toString())
        add(profile.id)
        if (finalArgument) add(profile.name)
    }
}.distinct()
