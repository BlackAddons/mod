package org.blackaddons.core.commands

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.commands.ArgType
import org.blackaddons.api.commands.CommandBuilder
import org.blackaddons.api.commands.CommandContext
import org.blackaddons.api.commands.subcommands
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.cleanCommand
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.ShortcutAction
import org.blackaddons.core.actions.steps.SendMessageStep
import org.blackaddons.core.backend.BackendNotifier
import org.blackaddons.core.backend.Network
import org.blackaddons.core.backend.auth.AccountLink
import org.blackaddons.core.backend.auth.Elevation
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.features.impl.ui.inventorybuttons.InventoryButtonProfiles
import org.blackaddons.core.features.impl.ui.inventorybuttons.InventoryButtonStore
import org.blackaddons.core.features.impl.ui.inventorybuttons.InventoryButtons
import org.blackaddons.core.features.impl.visual.fme.Fme
import org.blackaddons.core.profile.RecentPlayers
import org.blackaddons.core.profile.friends.Friends
import org.blackaddons.core.ui.screen.ClickGuiScreen
import org.blackaddons.core.ui.screen.HudEditorScreen
import org.blackaddons.core.ui.screen.chat.ChatScreen
import org.blackaddons.core.ui.screen.fme.FmeScreen
import org.blackaddons.core.ui.screen.pv.ProfileViewerScreen
import org.blackaddons.core.ui.screen.screenshots.ScreenshotsScreen
import org.blackaddons.core.ui.screen.soloclears.SoloClearsScreen
import org.blackaddons.core.ui.screen.tickets.TicketScreen

/**
 * `/blackaddons` (`/b`, `/bl`, `/black`):
 * - no args -> ClickGUI
 * - `action` (`ac`, `actions`), `link`, `chat`, `clear`, `tickets`, `fme`, `elevate`, `reauth`, `pv [name]`, `soloclears`
 *   (`sc`), `screenshots` (`ss`), `net [socket|restart]`, `import`
 * Screens open a tick later, because the chat screen is still closing when a command runs.
 */
object BlackaddonsCommand {

    private const val NAME = "blackaddons"
    private val ALIASES = arrayOf("black", "b", "bl")

    @Volatile
    private var registered = false

    fun register() {
        if (registered) return
        registered = true

        Platform.commands.register(NAME, *ALIASES) {
            execute { openLater { ClickGuiScreen.open() } }

            subcommands("action", "ac", "actions") { registerActionsBranches() }

            subcommand("hud") {
                execute { openLater { HudEditorScreen.open() } }
            }

            subcommand("fme") {
                execute { openLater { FmeScreen.open() } }

                subcommand("toggle") {
                    execute { reply("Legit FME ${if (Fme.toggleEnabled()) "enabled" else "disabled"}.") }
                }
                subcommand("on") {
                    execute {
                        Fme.setEnabled(true)
                        reply("Legit FME enabled.")
                    }
                }
                subcommand("off") {
                    execute {
                        Fme.setEnabled(false)
                        reply("Legit FME disabled.")
                    }
                }
                subcommands("em", "edit", "editmode") {
                    execute { reply("Legit FME edit mode ${if (Fme.toggleEditMode()) "enabled" else "disabled"}.") }
                }
                subcommand("undo") {
                    execute { reply(if (Fme.undo()) "Undid the last FME edit." else "Nothing to undo.") }
                }
                subcommand("redo") {
                    execute { reply(if (Fme.redo()) "Redid the last FME edit." else "Nothing to redo.") }
                }
                subcommand("clear") {
                    execute {
                        val count = Fme.clearReplacements()
                        reply(if (count == 0) "No FME edits to clear." else "Cleared $count FME edit${if (count == 1) "" else "s"}.")
                    }
                }
            }

            subcommands("alias", "commandaliases") { registerAliasBranches() }

            subcommands("buttons", "invbuttons", "invbt") {
                requires { InventoryButtons.isRunning }
                execute { InventoryButtons.edit() }
                subcommand("profile") {
                    arg("profile", ArgType.GreedyStrArg) {
                        suggests { InventoryButtonStore.profiles.map { it.name } }
                        execute {
                            val name = str("profile")
                            val selected = InventoryButtonProfiles.selectNamed(name)
                            reply(ChatMessage.build {
                                brand()
                                if (selected) {
                                    append(I18n.trOr("blackaddons.inventory_buttons.command.selected", "Switched to button profile %s.", name), Brand.OK)
                                } else {
                                    append(I18n.trOr("blackaddons.inventory_buttons.command.not_found", "No button profile is named %s.", name), Brand.FAIL)
                                }
                            })
                        }
                    }
                }
            }

            subcommand("link") {
                execute { AccountLink.requestCode() }
            }

            // Every echoed line's prefix leads here too.
            subcommand("chat") {
                execute { openLater { ChatScreen.open() } }
            }

            subcommand("clear") {
                execute { Platform.chat.clear() }
            }

            subcommand("tickets") {
                execute { openLater { TicketScreen.open() } }
                arg("id", ArgType.IntArg(min = 1)) {
                    execute { openLater { TicketScreen.open(int("id").toLong()) } }
                }
            }

            // No argument on purpose: a code typed without the slash would go to public chat.
            subcommand("elevate") {
                execute { Elevation.requestSignIn() }
            }

            // Also explains why a run didn't get on a board.
            subcommands("soloclears", "sc") {
                execute { openLater { SoloClearsScreen.open() } }
            }

            subcommands("screenshots", "ss") {
                execute { openLater { ScreenshotsScreen.open() } }
            }

            subcommand("net") {
                execute {
                    reply(ChatMessage.build {
                        brand()
                        append(I18n.tr("blackaddons.net.status", Gateway.describe()), Brand.BODY)
                    })
                }
                subcommand("socket") {
                    execute {
                        Network.restartSocket()
                        reply(ChatMessage.build { brand(); append(I18n.tr("blackaddons.net.socket_restarted"), Brand.OK) })
                    }
                }
                subcommand("restart") {
                    execute {
                        Network.restartAll()
                        reply(ChatMessage.build { brand(); append(I18n.tr("blackaddons.net.restarted"), Brand.OK) })
                    }
                }
            }

            subcommand("reauth") {
                execute { BackendNotifier.retry() }
            }

            subcommand("ping") {
                execute {
                    Platform.gameStats.measureActivePing { latency ->
                        val message = when {
                            latency < 0 -> "Ping failed (not connected or timed out)."
                            latency < 50 -> "Ping: ${latency}ms. US ping... zzzz..." // B: server room gaming
                            latency <= 9000 -> "Ping: ${latency}ms"
                            else -> "Ping: ${latency}ms. It's Over 9000! Hehe, funny? No? I'm sorry ;("
                        }

                        reply(
                            ChatMessage.build {
                                brand()
                                append(message, Brand.BODY)
                            },
                        )
                    }
                }
            }

            // Reads the clipboard, because a long argument would land in everyone's chat log. Shows a diff first.
            subcommand("import") {
                execute {
                    when (val import = PortableImports.importAny(Platform.clipboard.paste())) {
                        is PortableImports.AnyImport.Refused -> reply(ChatMessage.build { brand(); append(import.message, Brand.FAIL) })
                        is PortableImports.AnyImport.Opens -> openLater(import.open)
                    }
                }
            }

            subcommand("pv") {
                arg("name", ArgType.PlayerArg, optional = true) {
                    // Recent lookups and friends, not the tab list.
                    suggests { RecentPlayers.list().map { it.name } + Friends.names() }
                    execute {
                        val name = strOrNull("name") ?: Platform.player.name ?: return@execute
                        openLater { ProfileViewerScreen.open(name) }
                    }
                }
            }
        }
    }

    private fun CommandBuilder.registerAliasBranches() {
        execute { listAliases(this) }
        subcommand("list") {
            execute { listAliases(this) }
        }
        subcommand("add") {
            arg("name", ArgType.StrArg) {
                arg("command", ArgType.GreedyStrArg) {
                    execute {
                        val aliasName = str("name").cleanCommand()
                        val targetCommand = str("command")
                        val existing = ActionStorage.shortcuts.firstOrNull { it.alias.equalsIgnoreCase(aliasName) }
                        if (existing != null) {
                            existing.name = aliasName
                            existing.alias = aliasName
                            existing.actions.clear()
                            existing.actions.add(ActionStep.of(SendMessageStep, SendMessageStep.Params(targetCommand)))
                        } else {
                            val newShortcut = ShortcutAction(
                                name = aliasName,
                                alias = aliasName,
                                enabled = true,
                                actions = mutableListOf(ActionStep.of(SendMessageStep, SendMessageStep.Params(targetCommand))),
                            )
                            ActionStorage.shortcuts.add(newShortcut)
                        }
                        ActionStorage.save()
                        reply(
                            ChatMessage.build {
                                brand()
                                append("Saved command alias ", Brand.BODY)
                                append("/$aliasName", Theme.ACCENT)
                                append(" -> ", Theme.TEXT_MUTED)
                                append(targetCommand, Brand.BODY)
                            },
                        )
                    }
                }
            }
        }
        subcommand("del") {
            arg("name", ArgType.StrArg) {
                suggests { ActionStorage.shortcuts.mapNotNull { it.alias.ifBlank { null } } }
                execute {
                    val aliasName = str("name").cleanCommand()
                    val removed = ActionStorage.shortcuts.removeIf { it.alias.equalsIgnoreCase(aliasName) }
                    if (removed) {
                        ActionStorage.save()
                        reply(
                            ChatMessage.build {
                                brand()
                                append("Deleted command alias ", Brand.BODY)
                                append("/$aliasName", Theme.ACCENT)
                            },
                        )
                    } else {
                        reply(
                            ChatMessage.build {
                                brand()
                                append("Command alias ", Brand.BODY)
                                append("/$aliasName", Theme.ACCENT)
                                append(" not found.", Brand.FAIL)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun listAliases(ctx: CommandContext) {
        val aliases = ActionStorage.shortcuts.filter { it.alias.isNotBlank() }
        if (aliases.isEmpty()) {
            ctx.reply(
                ChatMessage.build {
                    brand()
                    append("No command aliases configured in current profile.", Theme.TEXT_MUTED)
                },
            )
            return
        }
        ctx.reply(
            ChatMessage.build {
                brand()
                append("Command Aliases (${aliases.size}):", Theme.ACCENT)
            },
        )
        for (sc in aliases) {
            val target = sc.actions.firstNotNullOfOrNull { it.paramsOf(SendMessageStep) }?.message
                ?: "${sc.actions.size} action steps"
            val status = if (sc.enabled) "[ON]" else "[OFF]"
            ctx.reply(
                ChatMessage.build {
                    append("  $status ", if (sc.enabled) Brand.OK else Theme.TEXT_MUTED)
                    append("/${sc.alias}", Theme.ACCENT)
                    append(" -> ", Theme.TEXT_MUTED)
                    append(target, Brand.BODY)
                },
            )
        }
    }

    private fun openLater(open: () -> Unit) {
        Platform.scheduler.schedule(0) { open() }
    }
}
