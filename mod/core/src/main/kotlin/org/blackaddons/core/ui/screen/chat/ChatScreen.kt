package org.blackaddons.core.ui.screen.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.BackendNotifier
import org.blackaddons.core.backend.chat.ChatAttachments
import org.blackaddons.core.backend.chat.ChatChannel
import org.blackaddons.core.backend.chat.ChatLine
import org.blackaddons.core.backend.chat.ChatService
import org.blackaddons.core.backend.chat.ChatStore
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.chat.ChatMirror
import org.blackaddons.core.chat.ChatPalette
import org.blackaddons.core.emoji.CustomEmojiStore
import org.blackaddons.core.emoji.Emoji
import org.blackaddons.core.emoji.withEmoji
import org.blackaddons.core.media.Thumbnails
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * The BlackAddons chat window. It's the real chat, because only it can edit or delete lines ([ChatMirror] is just a
 * read shortcut). Viewing a channel stops its unread count.
 */
class ChatScreen : ModalPanel(I18n.tr("blackaddons.bachat.title")) {

    override val closeOnOutsideClick: Boolean get() = false

    // Near full screen. At 70% it looked like a floating widget.
    override val panelWidth: Float
        get() = (UiManager.screenWidth * WIDTH_FRACTION)
            .coerceIn(MIN_W, (UiManager.screenWidth - MARGIN).coerceAtLeast(MIN_W))

    override val panelHeight: Float
        get() = (UiManager.screenHeight * HEIGHT_FRACTION)
            .coerceIn(MIN_H, (UiManager.screenHeight - MARGIN).coerceAtLeast(MIN_H))

    private var channel = lastChannel
    private var replyTo: ChatLine? = null

    private val feed = ChatFeed(widgets).apply {
        onReply = { replyTo = it }
        onCopy = { copy(it.content) }
        onDelete = { line ->
            UiManager.confirmPopover(message = I18n.tr("blackaddons.bachat.delete_sure")) {
                ChatService.delete(line.id)
            }
        }
        onPicture = { line, picture -> ChatImageModal.open(picture, line.senderName) }
        onNearTop = { ChatService.loadOlder(channel) }
    }

    private val input = widgets.add(TextInput(
        placeholder = I18n.tr("blackaddons.bachat.placeholder"),
        maxLength = ChatService.MAX_CONTENT,
    ).apply {
        blurOnClickOutside = false
        focused = true
        onEnter = { submit() }
        // Escape closes the window and doesn't clear the field.
        onEscape = {}
        suggestions = ::emojiSuggestions
        suggestionDisplay = { candidate -> shortcodeLabels[candidate] ?: candidate }
    }.withEmoji())

    private var shortcodeLabels: Map<String, String> = emptyMap()

    private fun emojiSuggestions(text: String): List<String> {
        val fragment = SHORTCODE_FRAGMENT.find(text)?.groupValues?.get(1)?.lowercase()
        if (fragment == null) {
            shortcodeLabels = emptyMap()
            return emptyList()
        }
        val head = text.dropLast(fragment.length + 1)
        val labels = LinkedHashMap<String, String>()
        CustomEmojiStore.known.filter { it.name.lowercase().startsWith(fragment) }.take(SUGGESTIONS)
            .forEach { labels[head + it.token + " "] = ":${it.name}:" }
        Emoji.namesStartingWith(fragment, SUGGESTIONS).forEach { name ->
            Emoji.glyphFor(name)?.let { labels[head + Emoji.glyphText(it) + " "] = Emoji.glyphText(it) + "  :$name:" }
        }
        shortcodeLabels = labels
        return labels.keys.take(SUGGESTIONS)
    }

    private val sendButton = widgets.add(Button(I18n.tr("blackaddons.bachat.send")) { submit() }.apply { accent = true })

    /** Uploaded on attach, so Send only references it. */
    private class Attachment(val path: Path) {
        @Volatile var hash: String? = null
        @Volatile var refused: String? = null
        val uploading: Boolean get() = hash == null && refused == null
    }

    private val attachments = mutableListOf<Attachment>()
    private var attachNotice: String? = null
    private var attachNoticeAt = 0L

    override fun onShow() {
        BackendNotifier.ensureConnected()
        ChatStore.onViewing(channel)
        ChatService.probeStaff()
        if (!ChatStore.isBackfilled(channel)) ChatService.backfill(channel)
    }

    override fun onClose() {
        lastChannel = channel
        ChatStore.onViewing(null)
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        feed.selfUuid = ChatStore.selfUuid
        feed.lines = ChatStore.lines(channel)
        feed.loadingOlder = ChatService.isLoadingOlder(channel)

        drawChannels(contentX, contentY, mouseX, mouseY)
        drawConnection(contentX + contentW, contentY)

        val composerY = contentY + contentH - COMPOSER_H
        val stripY = if (attachments.isEmpty()) composerY else composerY - STRIP_H - GAP
        val statusY = stripY - STATUS_H - GAP
        val replyY = if (replyTo == null) statusY else statusY - REPLY_H
        val feedTop = contentY + TABS_H + GAP
        val feedH = (replyY - GAP - feedTop).coerceAtLeast(0f)

        feed.render(contentX, feedTop, contentW, feedH, mouseX, mouseY)

        if (replyTo != null) drawReplyBanner(contentX, replyY, contentW)
        drawStatus(contentX, statusY, contentW)
        if (attachments.isNotEmpty()) drawAttachments(contentX, stripY, mouseX, mouseY)
        drawComposer(contentX, composerY, contentW, mouseX, mouseY)
    }

    private fun drawChannels(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        var cursor = x
        for (option in ChatChannel.entries) {
            // Staff tab only after the server confirms access.
            if (option == ChatChannel.STAFF && ChatService.staffAccess != true) continue

            val unread = ChatStore.unread(option)
            val label = I18n.tr("blackaddons.bachat.channel." + option.id) +
                if (unread > 0 && option != channel) "  $unread" else ""
            val width = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium) + Theme.PADDING * 2f
            val active = option == channel
            val hovered = widgets.clickable(cursor, y, width, TAB_H) { select(option) }

            val fill = when {
                active -> Theme.ACCENT_SOFT
                hovered -> Theme.SURFACE_HOVER
                else -> Theme.SURFACE.withAlpha(90)
            }
            Render.rect(cursor, y, width, TAB_H, fill, Theme.RADIUS_SMALL)
            Render.textCentered(
                label, cursor + width / 2f, Render.centerY(y, TAB_H, Theme.FONT_SMALL, Fonts.medium),
                Theme.FONT_SMALL,
                if (active) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
                Fonts.medium,
            )
            cursor += width + GAP
        }
    }

    private fun drawConnection(right: Float, y: Float) {
        if (Gateway.connected) return
        val label = I18n.tr(if (Gateway.unsupported) "blackaddons.bachat.outdated" else "blackaddons.bachat.offline")
        val width = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
        Render.text(
            label, right - width, Render.centerY(y, TAB_H, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL, Theme.DANGER, Fonts.medium,
        )
    }

    private fun drawReplyBanner(x: Float, y: Float, w: Float) {
        val line = replyTo ?: return
        Render.rect(x, y, w, REPLY_H - 2f, Theme.SURFACE.withAlpha(120), Theme.RADIUS_SMALL)
        val label = I18n.tr("blackaddons.bachat.replying", line.senderName) + ": " + line.content
        Render.text(
            label,
            x + Theme.PADDING, Render.centerY(y, REPLY_H - 2f, TextStyle.SMALL_SECONDARY),
            TextStyle.SMALL_SECONDARY, maxWidth = w - CANCEL_W - Theme.PADDING * 2f,
        )

        val cancelX = x + w - CANCEL_W
        val over = widgets.clickable(cancelX, y, CANCEL_W, REPLY_H - 2f) { replyTo = null }
        Render.textCentered(
            I18n.tr("blackaddons.bachat.cancel_reply"), cancelX + CANCEL_W / 2f,
            Render.centerY(y, REPLY_H - 2f, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL, if (over) Theme.DANGER else Theme.TEXT_MUTED, Fonts.medium,
        )
    }

    /** One status line: refusal, then mute, then typing. */
    private fun drawStatus(x: Float, y: Float, w: Float) {
        val refusal = ChatStore.refusal?.takeIf { System.currentTimeMillis() - it.at < REFUSAL_MS }
        val mute = ChatStore.mute
        val typists = ChatStore.typists(channel)

        val notice = attachNotice?.takeIf { System.currentTimeMillis() - attachNoticeAt < REFUSAL_MS }
        val (text, color) = when {
            notice != null -> notice to Theme.DANGER
            // A failed upload blocks the message until it's removed.
            attachments.any { it.refused != null } ->
                tr("refused", "%s - remove it to send").format(attachments.first { it.refused != null }.refused) to Theme.DANGER
            refusal != null -> ChatMirror.reason(refusal.code, refusal.detail) to Theme.DANGER
            attachments.any { it.uploading } -> tr("uploading", "Uploading...") to Theme.TEXT_MUTED
            mute != null -> muteText(mute) to ChatPalette.CHANNEL_STAFF
            typists.isEmpty() -> return
            typists.size == 1 -> I18n.tr("blackaddons.bachat.typing_one", typists.first()) to Theme.TEXT_MUTED
            else -> I18n.tr("blackaddons.bachat.typing_many") to Theme.TEXT_MUTED
        }
        Render.text(
            text, x, Render.centerY(y, STATUS_H, Theme.FONT_SMALL, Fonts.regular),
            Theme.FONT_SMALL, color, Fonts.regular, maxWidth = w,
        )
    }

    private fun drawComposer(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        val overAttach = widgets.clickable(x, y, COMPOSER_H, COMPOSER_H) {
            Platform.desktop.chooseFile(tr("choose", "Attach a picture"), ChatAttachments.EXTENSIONS) { attach(listOf(it)) }
        }
        Render.rect(x, y, COMPOSER_H, COMPOSER_H, if (overAttach) Theme.SURFACE_HOVER else Theme.SURFACE, Theme.RADIUS_SMALL)
        Render.icon(
            LucideIcons.paperclip, centered(x, COMPOSER_H, ICON), centered(y, COMPOSER_H, ICON), ICON,
            if (overAttach) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
        )
        if (overAttach) Tooltip.show(tr("attach", "Attach a picture - or drop files onto the window"))

        val emojiX = x + COMPOSER_H + GAP
        val overEmoji = widgets.clickable(emojiX, y, COMPOSER_H, COMPOSER_H) {
            UiManager.push(EmojiPickerPopover(emojiX, y) { insert -> input.editor.insert(insert) })
        }
        Render.rect(emojiX, y, COMPOSER_H, COMPOSER_H, if (overEmoji) Theme.SURFACE_HOVER else Theme.SURFACE, Theme.RADIUS_SMALL)
        Render.icon(
            LucideIcons.smile, centered(emojiX, COMPOSER_H, ICON), centered(y, COMPOSER_H, ICON), ICON,
            if (overEmoji) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
        )
        if (overEmoji) Tooltip.show(tr("emoji_hint", "Emoji - or type :name:"))

        val fieldX = emojiX + COMPOSER_H + GAP
        val buttonW = sendButton.naturalWidth().coerceAtLeast(SEND_MIN_W)
        val fieldW = w - (fieldX - x) - buttonW - GAP
        input.render(fieldX, y, fieldW, COMPOSER_H, mouseX, mouseY)
        sendButton.enabled = canSend()
        sendButton.render(fieldX + fieldW + GAP, y, buttonW, COMPOSER_H, mouseX, mouseY)
    }

    private fun drawAttachments(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        var cx = x
        for (attachment in attachments) {
            Render.rect(cx, y, CHIP_W, STRIP_H, Theme.SURFACE, Theme.RADIUS_SMALL)
            val preview = Thumbnails.request(attachment.path, CHIP_W.toInt() * 2, STRIP_H.toInt() * 2)
            if (preview != null) {
                Render.image(preview, cx, y, CHIP_W, STRIP_H, Theme.RADIUS_SMALL)
            } else {
                Render.textCentered(
                    attachment.path.extension.uppercase(), cx + CHIP_W / 2f,
                    Render.centerY(y, STRIP_H, TextStyle.SMALL_MUTED_MEDIUM), TextStyle.SMALL_MUTED_MEDIUM,
                )
            }
            if (attachment.uploading) Render.rect(cx, y, CHIP_W, STRIP_H, Theme.BACKGROUND.withAlpha(140), Theme.RADIUS_SMALL)

            val over = hit(mouseX, mouseY, cx, y, CHIP_W, STRIP_H)
            val refused = attachment.refused
            when {
                refused != null -> Render.hollowRect(cx, y, CHIP_W, STRIP_H, 1.5f, Theme.DANGER, Theme.RADIUS_SMALL)
                over -> Render.hollowRect(cx, y, CHIP_W, STRIP_H, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
            }
            if (over) Tooltip.show(if (refused != null) "${attachment.path.name}\n$refused" else attachment.path.name)

            val removeX = cx + CHIP_W - REMOVE_SIZE - 2f
            val removeY = y + 2f
            val overRemove = widgets.clickable(removeX, removeY, REMOVE_SIZE, REMOVE_SIZE) { attachments -= attachment }
            Render.rect(removeX, removeY, REMOVE_SIZE, REMOVE_SIZE, Theme.BACKGROUND.withAlpha(if (overRemove) 230 else 170), Theme.RADIUS_SMALL)
            Render.icon(
                LucideIcons.circleX, removeX + 1f, removeY + 1f, REMOVE_SIZE - 2f,
                if (overRemove) Theme.DANGER else Theme.TEXT_SECONDARY,
            )
            cx += CHIP_W + GAP
        }
    }

    private fun canSend(): Boolean =
        (input.text.isNotBlank() || attachments.isNotEmpty()) && attachments.all { it.hash != null }

    fun attach(paths: List<Path>) {
        val usable = paths.filter { it.extension.lowercase() in ChatAttachments.EXTENSIONS }
        if (usable.size < paths.size) notice(tr("unsupported", "Only PNG, JPEG, GIF, WebP pictures and Lottie animations can be attached"))
        val room = ChatAttachments.MAX_PER_MESSAGE - attachments.size
        if (usable.size > room) notice(tr("too_many", "Up to %d pictures per message").format(ChatAttachments.MAX_PER_MESSAGE))
        for (path in usable.take(room.coerceAtLeast(0))) {
            val attachment = Attachment(path)
            attachments += attachment
            OffThread.run(
                name = "chat-attach",
                work = { ChatAttachments.upload(path) },
                onFailure = { ChatAttachments.Upload.Refused(tr("failed", "Upload failed")) },
                onResult = { result ->
                    when (result) {
                        is ChatAttachments.Upload.Stored -> attachment.hash = result.hash
                        is ChatAttachments.Upload.Refused -> attachment.refused = result.reason
                    }
                },
            )
        }
    }

    private fun notice(text: String) {
        attachNotice = text
        attachNoticeAt = System.currentTimeMillis()
    }

    override fun filesDropped(paths: List<Path>): Boolean {
        attach(paths)
        return true
    }

    // The composer keeps focus, and a click-through doesn't close the window.
    override fun contentClicked(mouse: MouseButtonInput): Boolean {
        super.contentClicked(mouse)
        widgets.focus(input)
        return true
    }

    override fun keyPressed(key: KeyInput): Boolean {
        if (key.code != Keys.ESCAPE) return super.keyPressed(key)
        if (replyTo == null) return keys.handle(key.code, key.modifiers)
        replyTo = null
        return true
    }

    override fun charTyped(typed: CharInput): Boolean {
        if (!super.charTyped(typed)) return false
        // Only after real typing. The server throttles to 3 s.
        ChatService.typing(channel)
        return true
    }

    private fun select(option: ChatChannel) {
        if (option == channel) return
        channel = option
        replyTo = null
        feed.follow()
        ChatStore.onViewing(option)
        if (!ChatStore.isBackfilled(option)) ChatService.backfill(option)
    }

    private fun submit() {
        if (!canSend()) return
        val text = input.text.trim()
        // A refused send keeps text and pictures. The reason already shows.
        if (!ChatService.send(channel, text, replyTo?.id, attachments.mapNotNull { it.hash })) return
        input.text = ""
        attachments.clear()
        replyTo = null
        feed.follow()
    }

    private fun copy(text: String) {
        Platform.clipboard.copy(text)
        Toasts.show(I18n.tr("blackaddons.bachat.copied"), kind = ToastKind.SUCCESS)
    }

    private fun muteText(mute: ChatStore.Mute): String {
        val until = mute.expiresAt?.let {
            CLOCK.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }
        return when {
            until != null && mute.reason != null -> I18n.tr("blackaddons.bachat.muted_until_reason", until, mute.reason)
            until != null -> I18n.tr("blackaddons.bachat.muted_until", until)
            mute.reason != null -> I18n.tr("blackaddons.bachat.muted_reason", mute.reason)
            else -> I18n.tr("blackaddons.bachat.muted")
        }
    }

    companion object {
        private val SHORTCODE_FRAGMENT = Regex("(?:^|\\s):([A-Za-z0-9_+-]{2,})$")
        private const val SUGGESTIONS = 8

        private val tr = I18n.scope("blackaddons.bachat.attach")

        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

        private var lastChannel = ChatChannel.GLOBAL

        private const val WIDTH_FRACTION = 0.9f
        private const val HEIGHT_FRACTION = 0.88f

        private const val MARGIN = 24f

        private const val MIN_W = 520f
        private const val MIN_H = 320f

        private const val TABS_H = 20f
        private const val TAB_H = 18f
        private const val STATUS_H = 11f
        private const val REPLY_H = 16f
        private const val COMPOSER_H = 22f
        private const val CANCEL_W = 46f
        private const val SEND_MIN_W = 56f
        private const val ICON = 12f
        private const val STRIP_H = 40f
        private const val CHIP_W = 64f
        private const val REMOVE_SIZE = 12f
        private const val GAP = 6f

        private const val REFUSAL_MS = 8_000L

        fun open() {
            UiManager.open(ChatScreen())
        }

        fun openWith(paths: List<Path>) {
            val screen = ChatScreen()
            UiManager.open(screen)
            screen.attach(paths)
        }
    }
}
