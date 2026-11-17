package org.blackaddons.core.ui.screen.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.CosmeticName
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.render.RichSpan
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.auth.Elevation
import org.blackaddons.core.backend.chat.ChatImages
import org.blackaddons.core.backend.chat.ChatLine
import org.blackaddons.core.backend.chat.ChatPicture
import org.blackaddons.core.backend.chat.ChatQuote
import org.blackaddons.core.chat.ChatPalette
import org.blackaddons.core.emoji.EmojiText
import org.blackaddons.core.emoji.plainEmoji
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Bottom-anchored message column with lines of variable height (so not a ScrollableList). Lines in a row from one
 * speaker share a header: avatar, cosmetic name, time. Heights are cached per line and width, because measuring 300
 * paragraphs per frame is way too slow.
 */
internal class ChatFeed(private val widgets: WidgetHost) {

    private class Measure(val contentHash: Int, val height: Float)

    var lines: List<ChatLine> = emptyList()

    /** Own lines (and all lines, for moderators) get a delete. */
    var selfUuid: UUID? = null

    var onReply: (ChatLine) -> Unit = {}
    var onCopy: (ChatLine) -> Unit = {}
    var onDelete: (ChatLine) -> Unit = {}
    var onPicture: (ChatLine, ChatPicture) -> Unit = { _, _ -> }

    /** Called every frame while the reader is within one screen of the top. The store decides whether there's more. */
    var onNearTop: () -> Unit = {}

    var loadingOlder = false

    private val bounds = Rect()
    private val faces = HashMap<UUID, String>()
    private var firstId: Long? = null
    private val measures = HashMap<Long, Measure>()
    private var measuredAt = 0f
    private val scroll = widgets.add(Scroll().apply { stickToEnd = true })

    /** Expanded links are per reading, not per message. They collapse again on reopen. */
    private val expanded = HashSet<Long>()

    fun render(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        bounds.set(x, y, w, h)
        Render.rect(x, y, w, h, Theme.SURFACE.withAlpha(90), Theme.RADIUS_SMALL)

        val textW = w - PAD * 2f - GUTTER - AVATAR - AVATAR_GAP
        if (textW != measuredAt) {
            measures.clear()
            measuredAt = textW
        }
        if (measures.size > lines.size * 2 + MEASURE_SLACK) measures.keys.retainAll(lines.mapTo(HashSet()) { it.id })

        var total = 0f
        var previous: ChatLine? = null
        for (line in lines) {
            total += heightOf(line, textW, groupsWith(previous, line))
            previous = line
        }

        // New lines don't move a reader who scrolled up, and neither do older ones paged in above.
        scroll.shift(prependedHeight(textW))
        val offset = scroll.update(x, y, w, h, total)
        if (scroll.maxScroll > 0f && scroll.targetOffset < h) onNearTop()

        if (lines.isEmpty()) {
            emptyNotice(x, y, w, h)
            return
        }

        var cursor = if (total < h) y + h - total else y - offset
        previous = null
        Render.pushScissor(x, y, w, h)
        for (line in lines) {
            val grouped = groupsWith(previous, line)
            val height = heightOf(line, textW, grouped)
            previous = line
            val top = cursor
            cursor += height
            if (top + height < y || top > y + h) continue

            drawRow(line, grouped, x, top, w, height, textW, mouseX, mouseY)
        }
        if (loadingOlder) loadingNotice(x, y, w)
        Render.popScissor()

        scroll.draw()
    }

    /**
     * The old first line is the anchor: everything above it is new, and it might have lost its header to a line from
     * the same speaker that got paged in right before it.
     */
    private fun prependedHeight(textW: Float): Float {
        val anchor = firstId
        firstId = lines.firstOrNull()?.id
        val at = lines.indexOfFirst { it.id == anchor }
        if (at <= 0) return 0f
        var above = 0f
        var previous: ChatLine? = null
        for (line in lines.subList(0, at + 1)) {
            above += heightOf(line, textW, groupsWith(previous, line))
            previous = line
        }
        return above - heightOf(lines[at], textW, grouped = false)
    }

    private fun loadingNotice(x: Float, y: Float, w: Float) {
        val message = I18n.tr("blackaddons.bachat.loading")
        val width = Render.textWidth(message, Theme.FONT_SMALL, Fonts.regular) + PAD * 2f
        val left = centered(x, w, width)
        Render.rect(left, y + PAD, width, LOADING_H, Theme.SURFACE.withAlpha(220), Theme.RADIUS_SMALL)
        Render.text(message, left + PAD, Render.centerY(y + PAD, LOADING_H, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED)
    }

    fun follow() {
        scroll.reset()
    }

    private fun drawRow(
        line: ChatLine, grouped: Boolean, x: Float, top: Float, w: Float, height: Float, textW: Float,
        mouseX: Float, mouseY: Float,
    ) {
        val hovered = bounds.has(mouseX, mouseY) && hit(mouseX, mouseY, x, top, w, height)
        if (hovered) Render.rect(x, top, w, height, Theme.SURFACE_HOVER.withAlpha(70))

        var cursor = top + if (grouped) GROUP_GAP else BLOCK_GAP
        val textX = x + PAD + AVATAR + AVATAR_GAP

        line.quote?.let { quote ->
            drawQuote(quote, textX, cursor, textW)
            cursor += QUOTE_H
        }

        if (!grouped) {
            drawAvatar(line, x + PAD, cursor)
            val nameW = drawName(line, textX, cursor, mouseX, mouseY)
            val badgeW = drawSource(line, textX + nameW + PAD, cursor)
            val stamp = clock(line.createdAt) + if (line.edited) "  " + I18n.tr("blackaddons.bachat.edited") else ""
            Render.text(
                stamp, textX + nameW + PAD + badgeW + PAD, cursor + 1f, TextStyle.SMALL_MUTED,
            )
            cursor += HEADER_H
        }

        if (bodyOf(line).isNotBlank()) {
            Render.richText(spansOf(line, grouped), textX, cursor, textW, TextStyle.BODY_PRIMARY)
            cursor += contentHeight(line, textW, grouped)
        }

        if (line.links.isNotEmpty()) {
            drawLinkChip(line, textX, cursor)
            cursor += CHIP_H
        }

        line.pictures.forEach { picture ->
            val scale = picture.fitScale(textW, MAX_THUMB_H)
            val width = picture.boxWidth(MAX_THUMB_H) * scale
            val height = picture.boxHeight(MAX_THUMB_H) * scale
            drawPicture(picture, textX, cursor, width, height)
            widgets.clickable(textX, cursor, width, height) { onPicture(line, picture) }
            cursor += height + THUMB_GAP
        }

        if (hovered) drawActions(line, x + w - PAD, top + 2f)
    }

    /**
     * The cosmetic name as it looks in the world, with the real name in a tooltip when the cosmetic renames the player.
     * Returns the width.
     */
    private fun drawName(line: ChatLine, x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        val cosmetic = Platform.cosmetics.nameFor(line.playerUuid)
        val width = CosmeticName.draw(
            cosmetic, line.senderName, x, y, Theme.FONT_BODY, Fonts.medium, ChatPalette.nameColor(line.source),
        )
        val renamed = CosmeticName.textOf(cosmetic, line.senderName) != line.senderName
        if (renamed && bounds.has(mouseX, mouseY) && hit(mouseX, mouseY, x, y, width, HEADER_H)) {
            Tooltip.show(line.senderName)
        }
        return width
    }

    /**
     * Where the line came from, with the same badges as the site. The cosmetic name doesn't show it through its color
     * anymore.
     */
    private fun drawSource(line: ChatLine, x: Float, y: Float): Float {
        val label = I18n.tr("blackaddons.bachat.source." + line.source.name.lowercase())
        val width = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium) + BADGE_PAD * 2f
        val top = centered(y, HEADER_H, BADGE_H)
        val tint = ChatPalette.nameColor(line.source)
        Render.rect(x, top, width, BADGE_H, tint.withAlpha(40), Theme.RADIUS_SMALL)
        Render.text(label, x + BADGE_PAD, Render.centerY(top, BADGE_H, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, tint, Fonts.medium)
        return width
    }

    // "↪ Name: excerpt" on one line: the name as it's worn, and the excerpt cut to fit what's left.
    private fun drawQuote(quote: ChatQuote, x: Float, y: Float, w: Float) {
        Render.text(REPLY_MARK, x, y, TextStyle.SMALL_MUTED)
        var cursor = x + Render.textWidth(REPLY_MARK, Theme.FONT_SMALL, Fonts.regular)
        val cosmetic = Platform.cosmetics.nameFor(quote.playerUuid)
        cursor += CosmeticName.draw(cosmetic, quote.senderName, cursor, y, Theme.FONT_SMALL, Fonts.medium, Theme.TEXT_MUTED)
        val rest = x + w - cursor
        if (rest <= 0f) return
        Render.text(": " + plainEmoji(quote.excerpt), cursor, y, TextStyle.SMALL_MUTED, maxWidth = rest)
    }

    /**
     * The Minecraft face for linked authors and the Discord avatar for the rest. An initial shows until either one has
     * loaded.
     */
    private fun drawAvatar(line: ChatLine, x: Float, y: Float) {
        val url = avatarOf(line)
        if (url != null && Render.imageSize(url) != null) {
            Render.image(url, x, y, AVATAR, AVATAR, Theme.RADIUS_SMALL)
            return
        }
        Render.rect(x, y, AVATAR, AVATAR, ChatPalette.nameColor(line.source).withAlpha(70), Theme.RADIUS_SMALL)
        val initial = line.senderName.firstOrNull()?.uppercase() ?: "?"
        Render.textCentered(
            initial, x + AVATAR / 2f, Render.centerY(y, AVATAR, Theme.FONT_BODY, Fonts.medium),
            Theme.FONT_BODY, Theme.TEXT_PRIMARY, Fonts.medium,
        )
    }

    private fun avatarOf(line: ChatLine): String? {
        val uuid = line.playerUuid ?: return line.discordAvatar
        return faces.getOrPut(uuid) {
            "${Backend.MOJANG_AVATAR_URL}/${uuid.toString().replace("-", "")}/$AVATAR_PIXELS"
        }
    }

    /** Hover buttons right to left, so the row keeps its shape with or without delete. Icons with tooltips, not words. */
    private fun drawActions(line: ChatLine, right: Float, top: Float) {
        var cursor = right
        val mine = line.playerUuid
        /*
         * Null-safe ownership: `null == null` would offer delete on every Discord line. Other people's lines need a
         * session that can moderate (2FA-raised for staff).
         */
        val own = mine != null && mine == selfUuid
        if (own || Elevation.allows(Elevation.CHAT_MODERATE)) {
            cursor = action(ChatIcons.Icon.TRASH, "delete", cursor, top, Theme.DANGER) { onDelete(line) }
        }
        cursor = action(ChatIcons.Icon.COPY, "copy", cursor, top, Theme.ACCENT) { onCopy(line) }
        action(ChatIcons.Icon.REPLY, "reply", cursor, top, Theme.ACCENT) { onReply(line) }
    }

    private fun action(icon: ChatIcons.Icon, key: String, right: Float, top: Float, accent: Color, onClick: () -> Unit): Float {
        val left = right - ACTION_H
        val over = widgets.clickable(left, top, ACTION_H, ACTION_H, onClick = onClick)
        Render.rect(left, top, ACTION_H, ACTION_H, if (over) accent else Theme.SURFACE_ACTIVE, Theme.RADIUS_SMALL)
        ChatIcons.draw(icon, left, top, ACTION_H, Theme.TEXT_PRIMARY)
        if (over) Tooltip.show(I18n.tr("blackaddons.bachat.$key"))
        return left - ACTION_GAP
    }

    private fun emptyNotice(x: Float, y: Float, w: Float, h: Float) {
        val message = I18n.tr("blackaddons.bachat.empty")
        val width = Render.textWidth(message, Theme.FONT_SMALL, Fonts.regular)
        Render.text(
            message, centered(x, w, width), Render.centerY(y, h, TextStyle.SMALL_MUTED),
            TextStyle.SMALL_MUTED,
        )
    }

    private fun heightOf(line: ChatLine, textW: Float, grouped: Boolean): Float {
        var height = if (grouped) GROUP_GAP else BLOCK_GAP
        if (line.quote != null) height += QUOTE_H
        var below = if (grouped) 0f else HEADER_H
        if (bodyOf(line).isNotBlank()) below += contentHeight(line, textW, grouped)
        if (line.links.isNotEmpty()) below += CHIP_H
        for (picture in line.pictures) {
            below += picture.boxHeight(MAX_THUMB_H) * picture.fitScale(textW, MAX_THUMB_H) + THUMB_GAP
        }
        // A one-line message shouldn't let the next header move up into its avatar.
        if (!grouped) below = maxOf(below, AVATAR)
        return height + below + BOTTOM_GAP
    }

    private fun contentHeight(line: ChatLine, textW: Float, grouped: Boolean): Float {
        val cached = measures[line.id]
        // Custom emoji loading changes the layout, and so does the edited mark a line gets when it joins a group.
        val key = 31 * (31 * bodyOf(line).hashCode() + EmojiText.version) + if (marksEdit(line, grouped)) 1 else 0
        if (cached != null && cached.contentHash == key) return cached.height
        val height = Render.richTextBounds(spansOf(line, grouped), textW, Theme.FONT_BODY, Fonts.regular)[3]
        measures[line.id] = Measure(key, height)
        return height
    }

    // A grouped line has no header to show "(edited)", so it goes at the end of the text, same as on the site.
    private fun spansOf(line: ChatLine, grouped: Boolean): List<RichSpan> {
        val spans = EmojiText.spans(bodyOf(line))
        if (!marksEdit(line, grouped)) return spans
        return spans + RichSpan.Text(" " + I18n.tr("blackaddons.bachat.edited"), Theme.TEXT_MUTED, EDITED_SCALE)
    }

    private fun marksEdit(line: ChatLine, grouped: Boolean): Boolean = grouped && line.edited

    private fun bodyOf(line: ChatLine): String =
        if (line.id in expanded) line.content else line.shown

    /** Fold/unfold chip at the end, because word positions inside the wrapped paragraph can't be hit-tested. */
    private fun drawLinkChip(line: ChatLine, x: Float, y: Float) {
        val open = line.id in expanded
        val label = if (open) I18n.tr("blackaddons.bachat.link_hide")
        else I18n.tr("blackaddons.bachat.link_show", line.links.size)
        val width = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium) + PAD
        val over = widgets.clickable(x, y, width, CHIP_H - 2f) {
            if (!expanded.add(line.id)) expanded.remove(line.id)
            measures.remove(line.id)
        }

        Render.rect(x, y, width, CHIP_H - 2f, if (over) Theme.SURFACE_ACTIVE else Theme.SURFACE.withAlpha(120), Theme.RADIUS_SMALL)
        Render.textCentered(
            label, x + width / 2f, Render.centerY(y, CHIP_H - 2f, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL, if (over) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY, Fonts.medium,
        )
    }

    private fun groupsWith(previous: ChatLine?, line: ChatLine): Boolean =
        previous != null &&
            line.quote == null &&
            previous.source == line.source &&
            previous.senderName == line.senderName &&
            previous.playerUuid == line.playerUuid &&
            line.createdAt - previous.createdAt < GROUP_WINDOW_MS

    /** The box size comes from the message dimensions, so loading never reflows the feed. */
    private fun drawPicture(picture: ChatPicture, x: Float, y: Float, w: Float, h: Float) {
        if (!ChatImages.draw(picture, x, y, w, h, Theme.RADIUS_SMALL)) {
            Render.rect(x, y, w, h, Theme.SURFACE_ACTIVE.withAlpha(90), Theme.RADIUS_SMALL)
            val label = I18n.tr("blackaddons.bachat.picture")
            Render.textCentered(
                label, x + w / 2f, Render.centerY(y, h, TextStyle.SMALL_MUTED),
                TextStyle.SMALL_MUTED,
            )
        }
    }

    private fun clock(millis: Long): String =
        CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private companion object {
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        const val PAD = 8f

        const val GUTTER = 10f

        const val AVATAR = 20f

        const val BADGE_H = 11f
        const val BADGE_PAD = 4f
        const val AVATAR_GAP = 6f

        /** The same size the site and Discord ask for, so the backend serves all three from one cache entry. */
        const val AVATAR_PIXELS = 64

        const val QUOTE_H = 11f

        const val CHIP_H = 13f
        const val HEADER_H = 14f

        const val MAX_THUMB_H = 96f
        const val THUMB_GAP = 3f
        const val ACTION_H = 13f
        const val ACTION_GAP = 3f

        const val BLOCK_GAP = 7f
        const val GROUP_GAP = 1f
        const val BOTTOM_GAP = 2f
        const val LOADING_H = 14f

        const val GROUP_WINDOW_MS = 5 * 60 * 1000L

        /** The header's small text size, in ems of the body text. */
        const val EDITED_SCALE = Theme.FONT_SMALL / Theme.FONT_BODY

        const val REPLY_MARK = "↪ "

        /** So a busy channel doesn't sweep every frame. */
        const val MEASURE_SLACK = 32
    }
}
