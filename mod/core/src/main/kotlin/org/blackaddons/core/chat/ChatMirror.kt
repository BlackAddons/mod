package org.blackaddons.core.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.chat.RevisableLine
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.chat.ChatChannel
import org.blackaddons.core.backend.chat.ChatLine
import org.blackaddons.core.backend.chat.ChatSource
import org.blackaddons.core.backend.chat.ChatStore
import org.blackaddons.core.emoji.appendWithEmoji
import org.blackaddons.core.emoji.plainEmoji
import org.blackaddons.core.features.impl.settings.BlackaddonsChat
import java.awt.Color
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal object ChatPalette {

    val CHANNEL_STAFF = Color(0xFF9E4D)

    fun nameColor(source: ChatSource): Color = when (source) {
        ChatSource.MOD -> Theme.TEXT_PRIMARY
        ChatSource.WEB -> Color(0x8FB3FF)
        ChatSource.DISCORD -> Color(0x7B87F5)
        ChatSource.SYSTEM -> Theme.ACCENT
    }
}

/**
 * Echoes the BlackAddons chat into game chat while the window is closed. Lines stay revisable, so late pictures, edits
 * and retractions update in place. Called from gateway threads.
 */
internal object ChatMirror {

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Silent while the window shows the line. A line with only a link waits a bit for its picture, so we don't show a
     * raw-URL line that then reflows. Lines with words go out right away.
     */
    fun show(line: ChatLine) {
        if (!line.onlyLinks || line.pictures.isNotEmpty()) return say(line)
        holds[line.id] = timer.schedule({ if (holds.remove(line.id) != null) say(line) },
            LINK_GRACE_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * A picture joined a line that was sent without one (Discord sends text first). Revises the line, or sends a held
     * line now.
     */
    fun showLatePictures(line: ChatLine) {
        // Whoever removes the hold owns the echo, so the line is never sent twice.
        if (holds.remove(line.id)?.also { it.cancel(false) } != null) return say(line)
        // If nothing was sent, the window was showing the line.
        val said = said(line.id) ?: return
        said.revise(compose(line))
    }

    fun showRevised(line: ChatLine) {
        // A held line wasn't sent, but the hold has the old text. Taking the hold gives the echo back to [show].
        if (holds.remove(line.id)?.also { it.cancel(false) } != null) return show(line)
        said(line.id)?.revise(compose(line))
    }

    /** Marked, not removed (like a message deleted by a moderator), so nothing moves. */
    fun showRetracted(id: Long) {
        holds.remove(id)?.cancel(false)
        val said = said(id) ?: return
        said.revise(
            ChatMessage.build {
                openPrefix()
                append(I18n.tr("blackaddons.bachat.deleted"), Brand.BODY, italic = true)
            },
        )
    }

    private val holds = ConcurrentHashMap<Long, ScheduledFuture<*>>()

    private val timer = Workers.scheduled("chat-echo")

    /** Long enough for the bridge to fetch and store, short enough for links that never become pictures. */
    private const val LINK_GRACE_MS = 2500L

    private fun say(line: ChatLine) {
        if (!BlackaddonsChat.mirror || ChatStore.viewing == line.channel) return
        // Without revisable lines, late pictures go out as a continuation.
        val said = Platform.chat.revisable(compose(line)) ?: return
        synchronized(spoken) { spoken[line.id] = said }
    }

    private fun said(id: Long): RevisableLine? = synchronized(spoken) { spoken[id] }

    /** Bounded, like the chat's own 100-line history. */
    private val spoken = object : LinkedHashMap<Long, RevisableLine>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, RevisableLine>): Boolean =
            size > MAX_REVISABLE
    }

    private const val MAX_REVISABLE = 128

    private fun compose(line: ChatLine): ChatMessage = ChatMessage.build {
            openPrefix()
            if (line.channel == ChatChannel.STAFF) {
                append(I18n.tr("blackaddons.bachat.tag.staff") + " ", ChatPalette.CHANNEL_STAFF, bold = true)
            }
            line.quote?.let {
                append(
                    REPLY_MARK,
                    Brand.BODY,
                    hover = it.senderName + ": " + plainEmoji(it.excerpt),
                )
            }
            line.playerUuid?.let { appendHead(it, hover = line.senderName); append(" ") }
            append(line.senderName, ChatPalette.nameColor(line.source), hover = clock(line.createdAt))
            append(": ", Brand.BODY)
            // Uses `shown`, so a link that became the picture doesn't get read out.
            if (line.shown.isNotBlank()) appendWithEmoji(line.shown, Theme.TEXT_PRIMARY)
            if (line.edited) {
                append(" " + I18n.tr("blackaddons.bachat.edited"), Brand.BODY, italic = true)
            }
            // A drawn line can't grow, so the address goes in the hover, and a click copies it.
            for (link in line.links) {
                append(" ", Brand.BODY)
                append(
                    I18n.tr("blackaddons.bachat.link"),
                    Brand.OK,
                    underlined = true,
                    click = ChatClick.CopyToClipboard(link),
                    hover = link,
                )
            }
            for (picture in line.pictures) {
                /*
                 * Copy, never open (a browser that steals focus mid-fight). The link also lets the hover find the
                 * picture. No tooltip, because Minecraft draws it over our preview. The space is a separate span, so
                 * the underline skips it.
                 */
                append(" ", Brand.BODY)
                append(
                    I18n.tr("blackaddons.bachat.picture"),
                    Brand.OK,
                    underlined = true,
                    click = ChatClick.CopyToClipboard(Backend.CHAT_URL + "/attachments/" + picture.hash),
                )
            }
    }

    /** Only while the window is closed. Otherwise the composer shows it. */
    fun refused(code: String, detail: String?) {
        if (ChatStore.viewing != null) return
        Platform.chat.local(ChatMessage.build {
            openPrefix()
            append(reason(code, detail), Brand.FAIL)
        })
    }

    fun reason(code: String, detail: String?): String {
        if (code == TOO_NEW) opensIn(detail)?.let { return it }
        val text = I18n.trOr("blackaddons.bachat.error.$code", code)
        return if (detail.isNullOrBlank()) text else "$text ($detail)"
    }

    // `too_new` carries the instant the gate opens (ISO-8601, UTC), older servers send nothing.
    private fun opensIn(detail: String?): String? {
        val opensAt = detail?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val minutes = Duration.between(Instant.now(), opensAt).toMinutes().coerceAtLeast(1L)
        return I18n.tr("blackaddons.bachat.error.too_new_until", minutes / 60, minutes % 60)
    }

    /** True for a game chat line this mirror printed: it starts with the prefix that opens the window. */
    fun owns(message: ChatMessage): Boolean = message.spans.firstOrNull { it.text.isNotEmpty() }?.click == OPEN

    private val OPEN = ChatClick.RunCommand("blackaddons chat")

    /** The main way players find the window. */
    private fun ChatMessage.Builder.openPrefix() {
        appendGradient(
            BlackaddonsChat.tag + " ",
            Brand.FROM,
            Brand.TO,
            bold = true,
            click = OPEN,
            hover = I18n.tr("blackaddons.bachat.open_hover"),
        )
    }

    private fun clock(millis: Long): String =
        CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private const val REPLY_MARK = "↪ "
    private const val TOO_NEW = "too_new"
}
