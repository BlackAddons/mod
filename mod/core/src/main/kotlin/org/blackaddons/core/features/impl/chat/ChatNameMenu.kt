package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatLine
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.core.chat.ChatClipboard

/**
 * Adds a context menu to chat messages when right-clicked.
 * Fuck, this is so fucking clean, I love it.
 * TODO: Extend it if I have more ideas.
 */
object ChatNameMenu : Feature(
    description = "Right-click a chat message to copy it, or a player's message for profile, party, friend and message shortcuts.",
    name = "Chat Name Menu",
) {

    private val tr = I18n.scope("blackaddons.chat_name_menu")

    override fun init() {
        register<MouseClickEvent> {
            if (!event.isPress || event.button != Keys.MOUSE_RIGHT) return@register
            val x = Platform.window.mouseX
            val y = Platform.window.mouseY
            val line = Platform.chat.lineAt(x.toDouble(), y.toDouble()) ?: return@register
            val name = line.playerMessage?.player
            if (name == null && line.unformatted.isBlank()) return@register
            event.cancelled = true
            UiManager.contextMenuOver(x, y, entries(line, name))
        }
    }

    private fun entries(line: ChatLine, name: String?): List<ContextMenu.Entry> {
        val copyMessage = ContextMenu.Item(tr("copy_message", "Copy Message"), LucideIcons.copy) {
            ChatClipboard.copy(line, keepCodes = false)
        }
        return if (name == null) listOf(copyMessage) else playerEntries(name) + ContextMenu.Separator + copyMessage
    }

    private fun playerEntries(name: String): List<ContextMenu.Entry> = listOf(
        ContextMenu.Item(name, enabled = false) {},
        ContextMenu.Separator,
        ContextMenu.Item(tr("profile", "Open Profile Viewer"), LucideIcons.overview) { command("b pv $name") },
        ContextMenu.Item(tr("party", "Party Invite"), LucideIcons.usersRound) { command("p invite $name") },
        ContextMenu.Item(tr("friend", "Add Friend"), LucideIcons.userPlus) { command("f add $name") },
        ContextMenu.Item(tr("visit", "Visit Island"), LucideIcons.house) { command("visit $name") },
        ContextMenu.Item(tr("message", "Message"), LucideIcons.chat) { Platform.chat.openInput("/msg $name ") },
        ContextMenu.Item(tr("copy", "Copy Name"), LucideIcons.copy) {
            Platform.clipboard.copy(name)
            Toasts.show(tr("copied", "Name copied"), detail = name, kind = ToastKind.SUCCESS)
        },
    )

    private fun command(command: String) = Platform.chat.sendCommand(command)
}
