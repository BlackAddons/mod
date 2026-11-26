package org.blackaddons.api.ui.screen

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.chat.ChatSpan
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.hovered
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip.show
import org.blackaddons.api.ui.screen.Tooltip.showOver
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Scroll
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The single hover tooltip, painted by [UiManager] after the panel so it's never clipped. Last [show] in a
 * frame wins (the topmost hovered thing). Text drawn with a `maxWidth` shows its cut part here by itself.
 */
object Tooltip {

    /** So sweeping across a list stays quiet. */
    private val DELAY = 350.milliseconds

    private const val MAX_WIDTH = 220f

    // Hypixel wraps lore itself, and its lines are wider than the UI's.
    private const val ITEM_MAX_WIDTH = 280f
    private const val PADDING = 6f

    // Small vertical gap so the box hugs the cursor at high GUI scales.
    private const val CURSOR_GAP_X = 8f
    private const val CURSOR_GAP_Y = 3f

    /** [ITEM] is for Minecraft item lore: a proportional face with the lore's own bold and italic. */
    enum class Style { UI, ITEM }

    private var pending: List<ChatSpan>? = null
    private var pendingStyle = Style.UI
    private var shownText: List<ChatSpan>? = null
    private var style = Style.UI
    private var shownAt = TimeSource.Monotonic.markNow()
    private var drawn = false

    // Cached until the text changes; re-measuring long lore every frame thrashed the layout cache.
    private var lines: List<StyledLine> = emptyList()
    private val scroll = Scroll().apply { showTrack = false }

    /** Call every frame while hovered. */
    fun show(text: String) = show(ChatMessage.legacy(text))

    /** Shows [text] while the box is [hovered]. Call every frame, like [show]. */
    fun showOver(x: Float, y: Float, w: Float, h: Float, text: String?) {
        if (text != null && hovered(x, y, w, h)) show(text)
    }

    /** [showOver] for a laid-out box. */
    fun showOver(box: Rect, text: String?) = showOver(box.x, box.y, box.w, box.h, text)

    /** Keeps formatting such as obfuscated lore. */
    fun show(message: ChatMessage, style: Style = Style.UI) {
        val spans = normalized(message)
        if (spans.none { it.text.isNotBlank() }) return
        pending = spans
        pendingStyle = style
    }

    /** Once per frame, from [UiManager] or from a host drawing toolkit UI with no panel open. Never from a panel. */
    fun flush(mouseX: Float, mouseY: Float) {
        val text = pending
        pending = null

        if (text == null) {
            shownText = null
            drawn = false
            return
        }
        if (text != shownText || pendingStyle != style) {
            shownText = text
            style = pendingStyle
            shownAt = TimeSource.Monotonic.markNow()
            drawn = false
            scroll.reset()
            lines = emptyList()
            return
        }
        if (shownAt.elapsedNow() < DELAY) return
        if (lines.isEmpty()) lines = wrappedLines(text, if (style == Style.ITEM) ITEM_MAX_WIDTH else MAX_WIDTH)

        // No fade-in on purpose (tried and reverted): [DELAY] already waits for the reader to stop.
        draw(lines, mouseX, mouseY)
        drawn = true
    }

    /** Opt-in, so panels' own scroll areas keep priority. */
    fun mouseScrolled(amount: Float): Boolean = drawn && scroll.scrollBy(amount)

    /** Prevents a stale tooltip flash on reopen. */
    internal fun reset() {
        pending = null
        shownText = null
        drawn = false
        scroll.reset()
        lines = emptyList()
    }

    private fun draw(lines: List<StyledLine>, mouseX: Float, mouseY: Float) {
        val lineH = Render.textHeight(Theme.FONT_SMALL, fontFor(null))
        val textW = lines.maxOfOrNull(StyledLine::width) ?: 0f
        val textH = lines.size * lineH
        val viewportH = minOf(textH, (UiManager.screenHeight * MAX_HEIGHT_FRACTION - PADDING * 2f).coerceAtLeast(lineH))
        val scrollable = textH > viewportH
        val viewportW = textW + if (scrollable) SCROLLBAR_GUTTER else 0f
        val boxW = viewportW + PADDING * 2f
        val boxH = viewportH + PADDING * 2f

        // Right of the cursor, flipping at the edge.
        val screenW = UiManager.screenWidth
        val screenH = UiManager.screenHeight
        var x = mouseX + CURSOR_GAP_X
        var y = mouseY + CURSOR_GAP_Y
        if (x + boxW > screenW) x = (mouseX - CURSOR_GAP_X - boxW).coerceAtLeast(0f)
        if (y + boxH > screenH) y = (mouseY - CURSOR_GAP_Y - boxH).coerceAtLeast(0f)

        Render.glass(x, y, boxW, boxH, Glass.TOOLTIP)
        scroll.render(x + PADDING, y + PADDING, viewportW, viewportH, textH) { offset ->
            lines.forEachIndexed { index, line ->
                val lineY = y + PADDING + index * lineH - offset
                if (lineY + lineH < y + PADDING || lineY > y + PADDING + viewportH) return@forEachIndexed
                var lineX = x + PADDING
                line.pieces.forEach { (span, width) ->
                    val text = if (span.obfuscated) obfuscated(span.text) else span.text
                    val color = span.color ?: Theme.TEXT_PRIMARY
                    Render.text(text, lineX, lineY, Theme.FONT_SMALL, color, fontFor(span))
                    if (span.underlined) Render.rect(lineX, lineY + lineH - DECORATION, width, DECORATION, color)
                    if (span.strikethrough) Render.rect(lineX, lineY + lineH / 2f, width, DECORATION, color)
                    lineX += width
                }
            }
        }
    }

    /** Keeps per-span colors; the text renderer takes one color per call. */
    private fun wrappedLines(spans: List<ChatSpan>, maxWidth: Float): List<StyledLine> {
        val lines = mutableListOf<StyledLine>()
        val current = mutableListOf<Piece>()
        var width = 0f
        var pendingSpace = false

        fun finishLine() {
            lines += StyledLine(current.toList(), width)
            current.clear()
            width = 0f
            pendingSpace = false
        }

        fun append(span: ChatSpan, value: String, valueWidth: Float) {
            if (value.isEmpty()) return
            current += Piece(span.copy(text = value), valueWidth)
            width += valueWidth
        }

        spans.forEach { span ->
            WORD_OR_SPACE.findAll(span.text).forEach { match ->
                val token = match.value
                if (token == "\n") {
                    finishLine()
                } else if (token.isBlank()) {
                    pendingSpace = current.isNotEmpty()
                } else {
                    val font = fontFor(span)
                    var value = if (pendingSpace && current.isNotEmpty()) " $token" else token
                    var valueWidth = Render.textWidth(value, Theme.FONT_SMALL, font)
                    if (current.isNotEmpty() && width + valueWidth > maxWidth) {
                        finishLine()
                        value = token
                        valueWidth = Render.textWidth(token, Theme.FONT_SMALL, font)
                    }
                    append(span, value, valueWidth)
                    pendingSpace = false
                }
            }
        }
        if (current.isNotEmpty() || lines.isEmpty()) finishLine()
        return lines
    }

    // Unifont is monospace, so scrambled text keeps the original width. The UI style stays on the UI's own face.
    private fun fontFor(span: ChatSpan?): Font = when {
        span?.obfuscated == true -> Fonts.unifont
        style == Style.UI -> Fonts.regular
        span == null -> Fonts.sans
        else -> (if (span.bold) Fonts.sansBold else Fonts.sans).let { if (span.italic) it.copy(italic = true) else it }
    }

    internal fun obfuscated(text: String, frame: Long = System.currentTimeMillis() / OBFUSCATED_FRAME_MS): String =
        buildString(text.length) {
            text.forEachIndexed { index, char ->
                if (char.isWhitespace()) {
                    append(char)
                } else {
                    val pick = Math.floorMod(text.hashCode() + frame.hashCode() * 31 + index * 17, OBFUSCATED_GLYPHS.length)
                    append(OBFUSCATED_GLYPHS[pick])
                }
            }
        }

    /** Legacy-upgraded components can carry `§` codes inside styled spans. */
    internal fun normalized(message: ChatMessage): List<ChatSpan> = message.spans.flatMap { source ->
        ChatMessage.legacy(source.text).spans.map { parsed ->
            parsed.copy(
                color = parsed.color ?: source.color,
                bold = parsed.bold || source.bold,
                italic = parsed.italic || source.italic,
                underlined = parsed.underlined || source.underlined,
                strikethrough = parsed.strikethrough || source.strikethrough,
                obfuscated = parsed.obfuscated || source.obfuscated,
                click = source.click,
                hover = source.hover,
            )
        }
    }

    private data class Piece(val span: ChatSpan, val width: Float)

    private data class StyledLine(val pieces: List<Piece>, val width: Float)

    private val WORD_OR_SPACE = Regex("\\S+|\\n|[\\t ]+")
    private const val MAX_HEIGHT_FRACTION = 0.75f
    private const val SCROLLBAR_GUTTER = 8f
    private const val DECORATION = 1f
    private const val OBFUSCATED_FRAME_MS = 50L
    private const val OBFUSCATED_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
}
