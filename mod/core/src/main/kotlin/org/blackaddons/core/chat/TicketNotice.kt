package org.blackaddons.core.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.toast.ToastAction
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.tickets.TicketMessage
import org.blackaddons.core.backend.tickets.TicketSummary
import org.blackaddons.core.emoji.appendWithEmoji
import org.blackaddons.core.emoji.plainEmoji
import org.blackaddons.core.ui.screen.tickets.TicketScreen

/**
 * Ticket notices as a clickable chat line plus a toast. Runs on gateway threads, so screens open through the scheduler.
 */
internal object TicketNotice {

    private val tr = I18n.scope("blackaddons.tickets")

    /** A message in a ticket the window doesn't show. */
    fun message(ticket: TicketSummary, message: TicketMessage) {
        val text = message.content.ifBlank { tr("picture", "[picture]") }
        say(ticket.id) {
            append(tr("reply_from", "%s replied in #%d: ").format(message.authorName, ticket.id), Theme.ACCENT)
            appendWithEmoji(clip(text), Theme.TEXT_PRIMARY)
        }
        toast(tr("reply_toast", "%s replied in ticket #%d").format(message.authorName, ticket.id), clip(plainEmoji(text)), ticket.id)
    }

    fun opened(ticket: TicketSummary) {
        say(ticket.id) {
            append(tr("opened_line", "Staff opened ticket #%d with you: ").format(ticket.id), Theme.ACCENT)
            append(ticket.subject, Theme.TEXT_PRIMARY)
        }
        toast(tr("opened_toast", "Staff opened a ticket with you"), ticket.subject, ticket.id)
    }

    fun decided(ticket: TicketSummary) {
        val approved = ticket.request?.status == org.blackaddons.core.backend.tickets.TicketRequestStatus.APPROVED
        val headline = if (approved) tr("approved_line", "Your request in #%d was approved").format(ticket.id)
        else tr("rejected_line", "Your request in #%d was rejected").format(ticket.id)
        val reason = ticket.request?.reason
        say(ticket.id) {
            append(headline + if (reason != null) ": " else ". ", Theme.ACCENT)
            if (reason != null) append(clip(reason), Theme.TEXT_PRIMARY)
        }
        toast(headline, reason ?: ticket.subject, ticket.id)
    }

    fun waiting(count: Int) {
        say(null) {
            append(tr("waiting", "You have %d unread ticket replies. ").format(count), Theme.ACCENT)
        }
    }

    private fun say(ticketId: Long?, body: ChatMessage.Builder.() -> Unit) {
        val command = if (ticketId == null) "blackaddons tickets" else "blackaddons tickets $ticketId"
        Platform.chat.local(ChatMessage.build {
            appendGradient(
                I18n.tr("blackaddons.chat.prefix"), Brand.FROM, Brand.TO, bold = true,
                click = ChatClick.RunCommand(command),
            )
            body()
            append(" " + tr("open", "[Open]"), Brand.OK, underlined = true, click = ChatClick.RunCommand(command))
        })
    }

    private fun toast(text: String, detail: String, ticketId: Long) {
        Toasts.show(
            text, detail, ToastKind.INFO,
            actions = listOf(ToastAction({ tr("open_action", "Open") }) {
                Platform.scheduler.schedule(0) { TicketScreen.open(ticketId) }
            }),
        )
    }

    private fun clip(text: String): String =
        text.replace('\n', ' ').let { if (it.length > MAX_CHARS) it.take(MAX_CHARS) + "..." else it }

    private const val MAX_CHARS = 160
}
