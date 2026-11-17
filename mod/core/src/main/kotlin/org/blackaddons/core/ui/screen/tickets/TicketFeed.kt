package org.blackaddons.core.ui.screen.tickets

import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.tickets.TicketAuthor
import org.blackaddons.core.backend.tickets.TicketImages
import org.blackaddons.core.backend.tickets.TicketMessage
import org.blackaddons.core.emoji.EmojiText
import org.blackaddons.core.ui.screen.chat.boxHeight
import org.blackaddons.core.ui.screen.chat.boxWidth
import org.blackaddons.core.ui.screen.chat.fitScale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The lines of one ticket, bottom-anchored like chat. No replies, deletes or folded links: players only read and
 * answer.
 */
internal class TicketFeed(private val widgets: WidgetHost) {

    private class Measure(val contentHash: Int, val height: Float)

    var ticketId: Long = 0
    var messages: List<TicketMessage> = emptyList()

    private val bounds = Rect()
    private val measures = HashMap<Long, Measure>()
    private var measuredAt = 0f

    private val scroll = widgets.add(Scroll().apply { stickToEnd = true })

    fun render(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        bounds.set(x, y, w, h)
        Render.rect(x, y, w, h, Theme.SURFACE.withAlpha(90), Theme.RADIUS_SMALL)

        val textW = w - PAD * 2f - GUTTER
        if (textW != measuredAt) {
            measures.clear()
            measuredAt = textW
        }

        val total = messages.sumOf { heightOf(it, textW).toDouble() }.toFloat()
        scroll.render(x, y, w, h, total) { offset ->
            var cursor = if (total < h) y + h - total else y - offset
            for (message in messages) {
                val height = heightOf(message, textW)
                val top = cursor
                cursor += height
                if (top + height < y || top > y + h) continue
                if (message.author == TicketAuthor.SYSTEM) drawSystem(message, x, top, w)
                else drawLine(message, x + PAD, top, textW, mouseX, mouseY)
            }
        }
    }

    fun follow() {
        scroll.reset()
    }

    private fun drawSystem(message: TicketMessage, x: Float, top: Float, w: Float) {
        Render.textCentered(
            message.content + "  ·  " + clock(message.createdAt), x + w / 2f, top + GAP, TextStyle.SMALL_MUTED,
            maxWidth = w - PAD * 2f,
        )
    }

    private fun drawLine(message: TicketMessage, x: Float, top: Float, textW: Float, mouseX: Float, mouseY: Float) {
        var cursor = top + GAP
        val staff = message.author == TicketAuthor.STAFF
        val name = message.authorName.ifBlank { I18n.trOr("blackaddons.tickets.player", "Player") }
        Render.text(name, x, cursor, Theme.FONT_BODY, if (staff) Theme.ACCENT else Theme.TEXT_PRIMARY, Fonts.medium)
        var after = x + Render.textWidth(name, Theme.FONT_BODY, Fonts.medium) + PAD
        if (staff) {
            val tag = I18n.trOr("blackaddons.tickets.staff_tag", "staff")
            Render.text(tag, after, cursor + 1f, Theme.FONT_SMALL, Theme.ACCENT.withAlpha(200), Fonts.medium)
            after += Render.textWidth(tag, Theme.FONT_SMALL, Fonts.medium) + PAD
        }
        Render.text(clock(message.createdAt), after, cursor + 1f, TextStyle.SMALL_MUTED)
        cursor += HEADER_H

        if (message.content.isNotBlank()) {
            Render.richText(EmojiText.spans(message.content), x, cursor, textW, TextStyle.BODY_PRIMARY)
            cursor += contentHeight(message, textW)
        }

        for (picture in message.pictures) {
            val scale = picture.fitScale(textW, MAX_THUMB_H)
            val width = picture.boxWidth(MAX_THUMB_H) * scale
            val height = picture.boxHeight(MAX_THUMB_H) * scale
            if (!TicketImages.draw(ticketId, picture, x, cursor, width, height, Theme.RADIUS_SMALL)) {
                Render.rect(x, cursor, width, height, Theme.SURFACE_ACTIVE.withAlpha(90), Theme.RADIUS_SMALL)
            }
            if (bounds.has(mouseX, mouseY) && hit(mouseX, mouseY, x, cursor, width, height)) {
                Render.hollowRect(x, cursor, width, height, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
            }
            cursor += height + GAP
        }
    }

    private fun heightOf(message: TicketMessage, textW: Float): Float {
        if (message.author == TicketAuthor.SYSTEM) return SYSTEM_H
        var height = GAP + HEADER_H
        if (message.content.isNotBlank()) height += contentHeight(message, textW)
        for (picture in message.pictures) height += picture.boxHeight(MAX_THUMB_H) * picture.fitScale(textW, MAX_THUMB_H) + GAP
        return height + BOTTOM_GAP
    }

    private fun contentHeight(message: TicketMessage, textW: Float): Float {
        val cached = measures[message.id]
        val key = 31 * message.content.hashCode() + EmojiText.version
        if (cached != null && cached.contentHash == key) return cached.height
        val height = Render.richTextBounds(EmojiText.spans(message.content), textW, Theme.FONT_BODY, Fonts.regular)[3]
        measures[message.id] = Measure(key, height)
        return height
    }

    private fun clock(millis: Long): String = CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private companion object {
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

        const val PAD = 8f
        const val GUTTER = 10f
        const val GAP = 6f
        const val HEADER_H = 14f
        const val SYSTEM_H = 20f
        const val BOTTOM_GAP = 2f
        const val MAX_THUMB_H = 120f
    }
}
