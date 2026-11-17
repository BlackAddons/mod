package org.blackaddons.core.ui.screen.tickets

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.chat.ChatAttachments
import org.blackaddons.core.backend.tickets.TicketRequestStatus
import org.blackaddons.core.backend.tickets.TicketService
import org.blackaddons.core.backend.tickets.TicketStore
import org.blackaddons.core.backend.tickets.TicketSummary
import org.blackaddons.core.emoji.withEmoji
import org.blackaddons.core.media.Thumbnails
import java.nio.file.Path
import kotlin.io.path.extension

/** Ticket list left, the open ticket right. Staff open tickets, so there's no "new ticket", and empty is normal. */
internal class TicketScreen private constructor(private var selected: Long?) : ModalPanel(I18n.tr("blackaddons.tickets.title")) {

    override val closeOnOutsideClick: Boolean get() = false

    override val panelWidth: Float
        get() = (UiManager.screenWidth * 0.8f).coerceIn(MIN_W, (UiManager.screenWidth - MARGIN).coerceAtLeast(MIN_W))

    override val panelHeight: Float
        get() = (UiManager.screenHeight * 0.85f).coerceIn(MIN_H, (UiManager.screenHeight - MARGIN).coerceAtLeast(MIN_H))

    private val feed = TicketFeed(widgets)

    private val listScroll = widgets.add(Scroll())

    private val input = widgets.add(TextInput(
        placeholder = tr("placeholder", "Write a reply..."),
        maxLength = TicketService.MAX_CONTENT,
    ).apply {
        blurOnClickOutside = false
        onEnter = { submit() }
        onEscape = {}
    }.withEmoji())

    private val sendButton = widgets.add(Button(tr("send", "Send")) { submit() }.apply { accent = true })

    private class Attachment(val path: Path) {
        @Volatile var hash: String? = null
        @Volatile var refused: String? = null
        val uploading: Boolean get() = hash == null && refused == null
    }

    private val attachments = mutableListOf<Attachment>()

    // Set on the send worker and applied next frame, because the text field belongs to the render loop.
    @Volatile private var sending = false
    @Volatile private var sent = false
    @Volatile private var notice: String? = null

    override fun onShow() {
        TicketService.refresh()
        selected?.let(::select)
    }

    override fun onClose() {
        TicketStore.viewing = null
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        if (sent) {
            sent = false
            input.text = ""
            attachments.clear()
            feed.follow()
            sending = false
        }

        val tickets = TicketStore.tickets
        if (selected == null) tickets.firstOrNull()?.let { select(it.id) }

        drawList(tickets, contentX, contentY, LIST_W, contentH, mouseX, mouseY)

        val x = contentX + LIST_W + GAP
        val w = contentW - LIST_W - GAP
        val ticket = TicketStore.current?.takeIf { it.summary.id == selected }
        if (ticket == null) {
            val text = if (TicketStore.loaded && tickets.isEmpty()) tr("empty", "No tickets. Staff open one when they need something from you.")
            else tr("loading", "Loading...")
            Render.textCentered(
                text, x + w / 2f, Render.centerY(contentY, contentH, TextStyle.SMALL_MUTED),
                TextStyle.SMALL_MUTED,
            )
            return
        }

        drawHeader(ticket.summary, x, contentY, w)

        val composerY = contentY + contentH - COMPOSER_H
        val stripY = if (attachments.isEmpty()) composerY else composerY - STRIP_H - GAP
        val statusY = stripY - STATUS_H - GAP
        val feedTop = contentY + HEADER_H + GAP
        feed.ticketId = ticket.summary.id
        feed.messages = ticket.messages
        feed.render(x, feedTop, w, (statusY - GAP - feedTop).coerceAtLeast(0f), mouseX, mouseY)

        drawStatus(ticket.summary, x, statusY, w)
        if (!ticket.summary.open && !ticket.summary.appealable) return
        if (attachments.isNotEmpty()) drawAttachments(x, stripY)
        drawComposer(x, composerY, w, mouseX, mouseY)
    }

    private fun drawList(tickets: List<TicketSummary>, x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        Render.rect(x, y, w, h, Theme.SURFACE.withAlpha(90), Theme.RADIUS_SMALL)
        listScroll.render(x, y, w, h, tickets.size * (ROW_H + 2f) + GAP) { offset ->
            var cursor = y + GAP / 2f - offset
            for (ticket in tickets) {
                val top = cursor
                cursor += ROW_H + 2f
                if (top + ROW_H < y || top > y + h) continue

                val hitTop = top.coerceAtLeast(y)
                val hitH = (top + ROW_H).coerceAtMost(y + h) - hitTop
                val active = ticket.id == selected
                val hovered = widgets.clickable(x, hitTop, w, hitH) { select(ticket.id) }
                if (active || hovered) {
                    Render.rect(x + 2f, top, w - 4f, ROW_H, if (active) Theme.ACCENT_SOFT else Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
                }

                val badge = if (ticket.unread > 0) ticket.unread.toString() else if (!ticket.open) tr("closed", "closed") else null
                val badgeW = badge?.let { Render.textWidth(it, Theme.FONT_SMALL, Fonts.medium) + PAD } ?: 0f
                val subject = "#${ticket.id}  ${ticket.subject}"
                Render.text(
                    subject, x + PAD, top + 4f, Theme.FONT_SMALL,
                    if (ticket.open) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED, Fonts.medium,
                    maxWidth = w - PAD * 3f - badgeW,
                )
                Render.text(
                    ticket.openedBy,
                    x + PAD, top + 15f, TextStyle.SMALL_MUTED, maxWidth = w - PAD * 2f,
                )
                if (badge != null) {
                    val bx = x + w - PAD - badgeW
                    Render.rect(bx, top + 3f, badgeW, 11f, if (ticket.unread > 0) Theme.ACCENT else Theme.SURFACE_ACTIVE, Theme.RADIUS_SMALL)
                    Render.textCentered(
                        badge, bx + badgeW / 2f, Render.centerY(top + 3f, 11f, TextStyle.SMALL_PRIMARY_MEDIUM),
                        TextStyle.SMALL_PRIMARY_MEDIUM,
                    )
                }
            }
        }
    }

    private fun drawHeader(ticket: TicketSummary, x: Float, y: Float, w: Float) {
        val title = "#${ticket.id}  ${ticket.subject}"
        Render.text(title, x, y, TextStyle.BODY_PRIMARY_MEDIUM, maxWidth = w)
        val request = ticket.request
        val (line, color) = when (request?.status) {
            null -> tr("opened_by", "Opened by %s").format(ticket.openedBy) to Theme.TEXT_MUTED
            TicketRequestStatus.PENDING -> tr("request_pending", "Your request is waiting for staff") to Theme.ACCENT
            TicketRequestStatus.APPROVED -> tr("request_approved", "Your request was approved") to Theme.SUCCESS
            TicketRequestStatus.REJECTED -> tr("request_rejected", "Rejected - answer below to appeal") to Theme.DANGER
        }
        Render.text(line, x, y + 13f, Theme.FONT_SMALL, color, Fonts.regular, maxWidth = w)
    }

    private fun drawStatus(ticket: TicketSummary, x: Float, y: Float, w: Float) {
        val (text, color) = when {
            ticket.appealable && notice == null ->
                tr("appeal_notice", "Say why it should be reconsidered - sending reopens the request.") to Theme.TEXT_MUTED
            !ticket.open && !ticket.appealable ->
                tr("closed_notice", "This ticket is closed. Nothing more can be said in it until staff reopen it.") to Theme.TEXT_MUTED
            notice != null -> notice!! to Theme.DANGER
            attachments.any { it.refused != null } -> attachments.first { it.refused != null }.refused!! to Theme.DANGER
            attachments.any { it.uploading } -> tr("uploading", "Uploading...") to Theme.TEXT_MUTED
            sending -> tr("sending", "Sending...") to Theme.TEXT_MUTED
            else -> return
        }
        Render.text(
            text, x, Render.centerY(y, STATUS_H, Theme.FONT_SMALL, Fonts.regular), Theme.FONT_SMALL, color, Fonts.regular,
            maxWidth = w,
        )
    }

    private fun drawComposer(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        val overAttach = widgets.clickable(x, y, COMPOSER_H, COMPOSER_H) {
            Platform.desktop.chooseFile(tr("choose", "Attach a picture"), TicketService.EXTENSIONS) { attach(listOf(it)) }
        }
        Render.rect(x, y, COMPOSER_H, COMPOSER_H, if (overAttach) Theme.SURFACE_HOVER else Theme.SURFACE, Theme.RADIUS_SMALL)
        Render.icon(
            LucideIcons.paperclip, centered(x, COMPOSER_H, ICON), centered(y, COMPOSER_H, ICON), ICON,
            if (overAttach) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
        )
        if (overAttach) Tooltip.show(tr("attach", "Attach a picture - or drop files onto the window"))

        val fieldX = x + COMPOSER_H + GAP
        val buttonW = sendButton.naturalWidth().coerceAtLeast(SEND_MIN_W)
        val fieldW = w - (fieldX - x) - buttonW - GAP
        input.render(fieldX, y, fieldW, COMPOSER_H, mouseX, mouseY)
        sendButton.enabled = canSend()
        sendButton.render(fieldX + fieldW + GAP, y, buttonW, COMPOSER_H, mouseX, mouseY)
    }

    private fun drawAttachments(x: Float, y: Float) {
        var cx = x
        for (attachment in attachments) {
            Render.rect(cx, y, CHIP_W, STRIP_H, Theme.SURFACE, Theme.RADIUS_SMALL)
            Thumbnails.request(attachment.path, CHIP_W.toInt() * 2, STRIP_H.toInt() * 2)
                ?.let { Render.image(it, cx, y, CHIP_W, STRIP_H, Theme.RADIUS_SMALL) }
            if (attachment.uploading) Render.rect(cx, y, CHIP_W, STRIP_H, Theme.BACKGROUND.withAlpha(140), Theme.RADIUS_SMALL)
            if (attachment.refused != null) Render.hollowRect(cx, y, CHIP_W, STRIP_H, 1.5f, Theme.DANGER, Theme.RADIUS_SMALL)
            widgets.clickable(cx, y, CHIP_W, STRIP_H) { attachments -= attachment }
            cx += CHIP_W + GAP
        }
    }

    private fun canSend(): Boolean =
        !sending && (input.text.isNotBlank() || attachments.isNotEmpty()) && attachments.all { it.hash != null }

    private fun attach(paths: List<Path>) {
        val ticketId = selected ?: return
        val usable = paths.filter { it.extension.lowercase() in TicketService.EXTENSIONS }
        notice = if (usable.size < paths.size) tr("unsupported", "Only PNG, JPEG and GIF pictures can be attached") else null
        val room = TicketService.MAX_PICTURES - attachments.size
        for (path in usable.take(room.coerceAtLeast(0))) {
            val attachment = Attachment(path)
            attachments += attachment
            OffThread.run(
                name = "ticket-attach",
                work = {
                    ChatAttachments.upload(
                        path, Backend.TICKETS_URL + "/" + ticketId + "/attachments", TicketService.MAX_PICTURE_BYTES,
                    )
                },
                onFailure = { ChatAttachments.Upload.Refused(tr("upload_failed", "Upload failed")) },
                onResult = { result ->
                    when (result) {
                        is ChatAttachments.Upload.Stored -> attachment.hash = result.hash
                        is ChatAttachments.Upload.Refused -> attachment.refused = result.reason
                    }
                },
            )
        }
    }

    // How did we miss this HELLOOOO???? It's been a thing for like 5 versions... I blame Dream ;)
    override fun keyPressed(key: KeyInput): Boolean =
        if (key.code == Keys.ESCAPE) keys.handle(key.code, key.modifiers) else super.keyPressed(key)

    override fun filesDropped(paths: List<Path>): Boolean {
        attach(paths)
        return true
    }

    override fun contentClicked(mouse: MouseButtonInput): Boolean {
        super.contentClicked(mouse)
        widgets.focus(input)
        return true
    }

    private fun select(id: Long) {
        if (selected != id || TicketStore.current?.summary?.id != id) {
            selected = id
            attachments.clear()
            notice = null
            feed.follow()
            TicketService.open(id)
        }
        TicketStore.viewing = id
        widgets.focus(input)
    }

    private fun submit() {
        val id = selected ?: return
        if (!canSend()) return
        sending = true
        notice = null
        TicketService.reply(id, input.text.trim(), attachments.mapNotNull { it.hash }) { refusal ->
            // On success `sending` stays set until the field clears. Clearing it here let an early Enter post twice.
            if (refusal == null) {
                sent = true
            } else {
                notice = refusal
                sending = false
            }
        }
    }

    companion object {
        private val tr = I18n.scope("blackaddons.tickets")

        private const val MIN_W = 480f
        private const val MIN_H = 300f
        private const val MARGIN = 24f

        private const val LIST_W = 160f
        private const val ROW_H = 28f
        private const val HEADER_H = 26f
        private const val STATUS_H = 11f
        private const val COMPOSER_H = 22f
        private const val SEND_MIN_W = 56f
        private const val ICON = 12f
        private const val STRIP_H = 40f
        private const val CHIP_W = 64f
        private const val PAD = 6f
        private const val GAP = 6f

        fun open(id: Long? = null) {
            UiManager.open(TicketScreen(id))
        }
    }
}
