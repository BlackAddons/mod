package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatChannel
import org.blackaddons.api.chat.ChatLine
import org.blackaddons.api.chat.PlayerMessage
import org.blackaddons.api.event.chat.ChatLineAddedEvent
import org.blackaddons.api.event.chat.ChatScreenRenderEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.TabStrip
import java.awt.Color

object ChatMentions : Feature(
    description = "Highlights chat messages that mention your name or a keyword, with an optional sound.",
    name = "Chat Mentions",
) {

    private val yourName by boolean("Your Name", true)
        .withDescription("Messages that mention your username count as mentions")
    private val keywords by list("Keywords", placeholder = "Keyword")
        .withDescription("Extra words or phrases that count as mentions")
    private val highlightColor by color("Highlight Color", Color(255, 170, 0, 70))
        .withDescription("Tint drawn behind a message that mentions you")
    private val highlightWordOnly by boolean("Highlight Word Only", false)
        .withDescription("Tint only the matched name or keyword instead of the whole message")
    private val playSound by boolean("Play Sound", true)
        .withDescription("Ping when a mention arrives")
    private val mentionMode by boolean("Mention Mode", false)
        .withDescription("While chat is closed, only mentions and private messages to you show. Opening chat pins the ones that came in above it")
        .onChange { if (enabled) applyMentionMode() }
    private val unpinOn by mode("Unpin On", default = SCROLL_PAST, modes = listOf(SCROLL_PAST, CLICK))
        .withDescription("Clicking a pin scrolls to its message and drops it there. Scroll Past also drops one once you scroll up past its message")
        .showIf { mentionMode.value }

    private var terms: List<String> = emptyList()
    private var pattern: Regex? = null

    /** Oldest first; the newest sits right above the chat. */
    private val pins = ArrayDeque<ChatLine>()
    private var jumping: ChatLine? = null

    override fun init() {
        register<ChatLineAddedEvent> { arrived(event.line) }
        register<ChatScreenRenderEvent> { drawPins(event) }
        register<MouseClickEvent> {
            if (event.isPress && event.button == Keys.MOUSE_LEFT && Platform.screenHost.chatOpen && clickedPin()) {
                event.cancelled = true
            }
        }
    }

    override fun onEnable() = applyMentionMode()

    override fun onDisable() {
        pins.clear()
        jumping = null
        Platform.chat.showWhileClosed(null)
        super.onDisable()
    }

    private fun arrived(line: ChatLine) {
        val me = Platform.player.name
        if (mentions(line.unformatted, me)) {
            line.highlight = highlightColor.value
            if (highlightWordOnly.value) line.highlightMatches = pattern
            if (playSound.value) Platform.feedback.playSound(SOUND, pitch = SOUND_PITCH)
        }
        if (!mentionMode.value || Platform.screenHost.chatOpen || !forYou(line) || !Platform.chat.shows(line)) return
        pins.addLast(line)
        while (pins.size > MAX_PINS) pins.removeFirst()
    }

    private fun applyMentionMode() {
        if (!mentionMode.value) pins.clear()
        Platform.chat.showWhileClosed(if (mentionMode.value) ::forYou else null)
    }

    private fun forYou(line: ChatLine): Boolean =
        mentions(line.unformatted, Platform.player.name) || isIncomingPrivate(line.playerMessage)

    internal fun isIncomingPrivate(message: PlayerMessage?): Boolean =
        message?.channel == ChatChannel.PRIVATE && !message.outgoing

    internal fun mentions(text: String, me: String?): Boolean {
        if (pattern(me)?.containsMatchIn(text) != true) return false
        if (text.startsWith(OUTGOING_DM)) return false
        return me == null || !PlayerMessage.of(text)?.player.equals(me, ignoreCase = true)
    }

    private fun pattern(me: String?): Regex? {
        val current = buildList {
            if (yourName.value && me != null) add(me)
            keywords.value.mapNotNullTo(this) { it.trim().ifBlank { null } }
        }
        if (current != terms) {
            terms = current
            pattern = current.takeIf { it.isNotEmpty() }?.let { words ->
                Regex(words.joinToString("|", "(?<!\\w)(?:", ")(?!\\w)") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
            }
        }
        return pattern
    }

    private fun drawPins(event: ChatScreenRenderEvent) {
        if (pins.isEmpty()) return
        val lines = Platform.chat.lines
        val scrollPast = unpinOn.value == SCROLL_PAST
        pins.removeAll { pin ->
            val gone = lines.none { it === pin }
            gone || scrollPast && Platform.chat.scrolledPast(pin) || pin === jumping && Platform.chat.inView(pin)
        }
        if (jumping?.let { it !in pins } == true) jumping = null
        val area = Platform.chat.openLinesArea ?: return
        val hovered = pinAt(area, event.mouseX, event.mouseY)
        Render.push()
        Render.scale(SCALE, SCALE)
        try {
            pins.asReversed().forEachIndexed { index, pin ->
                val box = rowBox(area, index)
                if (box.y < 0f) return
                drawPin(pin, box, hover = if (pin === hovered) 1f else 0f)
            }
        } finally {
            Render.pop()
        }
    }

    private fun drawPin(pin: ChatLine, box: Rect, hover: Float) {
        Render.glass(box.x, box.y, box.w, box.h, Glass.BUTTON, hover)
        Render.icon(LucideIcons.pin, box.x + Theme.PADDING, box.y + (box.h - ICON_SIZE) / 2f, ICON_SIZE, Theme.ACCENT)
        val textX = box.x + Theme.PADDING * 2f + ICON_SIZE
        Render.pushScissor(textX, box.y, box.x + box.w - Theme.PADDING - textX, box.h)
        try {
            val textY = Render.centerY(box.y, box.h, Theme.FONT_SMALL, Fonts.regular)
            var spanX = textX
            for (span in pin.message.spans) {
                val text = span.text.substringBefore('\n')
                Render.text(text, spanX, textY, Theme.FONT_SMALL, span.color ?: Theme.TEXT_PRIMARY, Fonts.regular)
                spanX += Render.textWidth(text, Theme.FONT_SMALL, Fonts.regular)
                if (text.length < span.text.length) break
            }
        } finally {
            Render.popScissor()
        }
    }

	/** Jumps to the message; the pin goes once the chat has scrolled there. */
    private fun clickedPin(): Boolean {
        val area = Platform.chat.openLinesArea ?: return false
        val pin = pinAt(area, Platform.window.mouseX, Platform.window.mouseY) ?: return false
        jumping = pin
        Platform.chat.scrollTo(pin)
        return true
    }

    private fun pinAt(area: Rect, guiX: Float, guiY: Float): ChatLine? {
        val x = guiX / SCALE
        val y = guiY / SCALE
        return pins.asReversed().filterIndexed { index, _ ->
            val box = rowBox(area, index)
            x >= box.x && x <= box.x + box.w && y >= box.y && y <= box.y + box.h
        }.firstOrNull()
    }

    /** Row [index] counted up from the chat, newest first, in toolkit units. */
    private fun rowBox(area: Rect, index: Int): Rect {
        val bottom = area.y / SCALE - ROW_GAP
        val top = bottom - (index + 1) * ROW_HEIGHT - index * ROW_GAP
        return Rect(area.x / SCALE + Theme.PADDING_SMALL, top, area.w / SCALE - Theme.PADDING_SMALL * 2f, ROW_HEIGHT)
    }

    private const val OUTGOING_DM = "To "
    // TODO: add customization for sound and pitch
    private const val SOUND = "minecraft:block.note_block.pling"
    private const val SOUND_PITCH = 1.5f

    private const val SCROLL_PAST = "Scroll Past"
    private const val CLICK = "Click"
    private const val MAX_PINS = 3

    private const val ROW_HEIGHT = 20f
    private const val ROW_GAP = 3f
    private const val ICON_SIZE = 12f
    private const val CHAT_TABS_GUI_HEIGHT = 20f
    private const val SCALE = CHAT_TABS_GUI_HEIGHT / TabStrip.HEIGHT
}
