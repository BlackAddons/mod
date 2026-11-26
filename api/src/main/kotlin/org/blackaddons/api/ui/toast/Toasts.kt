package org.blackaddons.api.ui.toast

import org.blackaddons.api.HostApi
import org.blackaddons.api.Platform
import org.blackaddons.api.hud.*
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.stripFormatting
import java.awt.Color
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Picks icon and color. */
enum class ToastKind {
    INFO, SUCCESS, ERROR;

    internal val icon: VectorIcon
        get() = when (this) {
            INFO -> LucideIcons.circleInfo
            SUCCESS -> LucideIcons.circleCheck
            ERROR -> LucideIcons.circleX
        }

    internal val color: Color
        get() = when (this) {
            INFO -> Theme.ACCENT
            SUCCESS -> Theme.SUCCESS
            ERROR -> Theme.DANGER
        }
}

/** [aspect] is width / height. */
class ToastImage(val path: String, val aspect: Float)

/** [label] is read every frame; [dismisses] closes the notice on click. */
class ToastAction(val label: () -> String, val dismisses: Boolean = true, val onClick: () -> Unit)

/**
 * Short notices the player doesn't need to act on ("copied", "saved"). Use chat for things they must re-read or click.
 * The stack is a [HudElement] placed in the HUD editor. Thread-safe.
 */
object Toasts {

    private const val MAX_SHOWN = 4
    private const val MAX_QUEUED = MAX_SHOWN * 4

    private const val SLIDE_MS = 520L
    private const val SLIDE = 56f

    private const val PAD_X = 8f
    private const val PAD_Y = 6f
    private const val GAP = 4f
    private const val STRIP = 2f
    private const val ICON = 11f
    private const val ICON_GAP = 6f
    private const val DETAIL_GAP = 2f
    private const val BAR = 1.5f
    private const val IMAGE_W = 200f
    private const val IMAGE_GAP = 6f
    private const val ACTIONS_GAP = 6f
    private const val BUTTON_H = 16f
    private const val BUTTON_PAD = 7f
    private const val BUTTON_GAP = 4f

    /** 4 looked square and 8 like a pill at this size. */
    private const val CARD_RADIUS = 6f

    /** Sentences wrap rather than sprawl. */
    private const val MAX_TEXT_WIDTH = 190f
    private const val MAX_TITLE_LINES = 2
    private const val MAX_DETAIL_LINES = 4

    /** Below the panel's: a toast overlays the game, not a screen. */
    private const val CARD_ALPHA = 190
    private const val BAR_ALPHA = 170

    /** Lifetime = base + per character. */
    private const val BASE_LIFETIME_MS = 1500L
    private const val LIFETIME_PER_CHAR_MS = 41L
    private const val MIN_LIFETIME_MS = 2600L
    private const val MAX_LIFETIME_MS = 6750L

    /** Set by the host. */
    var durationScale: () -> Float = { 1f }
        @HostApi set

    private class Card(
        val title: List<String>,
        val detail: List<String>,
        val imageH: Float,
        val actionWidths: List<Float>,
        val width: Float,
        val height: Float,
    )

    private class Toast(
        val text: String,
        val detail: String?,
        val kind: ToastKind,
        val accent: Color,
        val image: ToastImage?,
        val actions: List<ToastAction>,
    ) {
        val shownAt = TimeSource.Monotonic.markNow()
        val lifetimeMs = (
            (BASE_LIFETIME_MS + LIFETIME_PER_CHAR_MS * (text.stripFormatting().length + (detail?.stripFormatting()?.length ?: 0)))
                .coerceIn(MIN_LIFETIME_MS, MAX_LIFETIME_MS) * durationScale()
            ).toLong()

        // 0 = gone, 1 = in place; drives fade, slide and the space taken.
        val reveal = Animation(initial = 0f, durationMs = SLIDE_MS, easing = Easing.easeInOut)

        // Built on the render thread on first draw.
        var card: Card? = null

        // Pushed out early, but still leaves with the normal animation.
        @Volatile var dismissed = false

        // Hover time doesn't count, so it can't expire under the pointer.
        var held = Duration.ZERO
        var lastSeen = shownAt
        var hovered = false

        fun remaining(now: TimeSource.Monotonic.ValueTimeMark): Float = 1f - ((now - shownAt - held) / lifetimeMs.milliseconds).toFloat()

        fun leaving(now: TimeSource.Monotonic.ValueTimeMark): Boolean = dismissed || remaining(now) <= 0f
    }

    private class Shown(
        val toast: Toast?,
        val card: Card,
        val kind: ToastKind,
        val accent: Color,
        val reveal: Float,
        val remaining: Float,
    ) {
        val image: ToastImage? get() = toast?.image
        val actions: List<ToastAction> get() = toast?.actions.orEmpty()
    }

    private class Hit(val x: Float, val y: Float, val w: Float, val h: Float, val onHit: () -> Unit) {
        fun has(px: Float, py: Float) = hit(px, py, x, y, w, h)
    }

    private val toasts = ArrayDeque<Toast>()

    // The HUD pass is blitted under open screens, so only the visible pass draws.
    private var screenPass = false

    // Buttons first, then cards, so a press on a card body is still taken.
    private var hits: List<Hit> = emptyList()

    val element: HudElement = object : HudElement() {
        override val name = "Notifications"
        override val drawsOverScreens = true
        override val shouldDraw: Boolean
            get() = hasToasts() && screenPass == Platform.screenHost.anyScreenOpen
        override val defaultAnchorX get() = AnchorX.RIGHT
        override val defaultAnchorY get() = AnchorY.TOP
        override val defaultOffsetX get() = -5f
        override val defaultOffsetY get() = 5f
        override fun content(example: Boolean): LayoutNode = stack(if (example) examples() else live())
        override fun mouseClicked(mouse: MouseButtonInput): Boolean {
            if (!mouse.isLeft) return false
            val hit = hits.firstOrNull { it.has(mouse.x, mouse.y) } ?: return false
            hit.onHit()
            return true
        }
    }.also { it.resetToDefault() }

    /**
     * [detail] is the quieter second line (what was copied, which file). Both wrap and accept `§` codes.
     * [actions] are clickable while the cursor is free.
     */
    fun show(
        text: String,
        detail: String? = null,
        kind: ToastKind = ToastKind.INFO,
        image: ToastImage? = null,
        actions: List<ToastAction> = emptyList(),
    ) = enqueue(Toast(text, detail?.takeIf { it.isNotBlank() }, kind, kind.color, image, actions))

    fun show(text: String, detail: String?, accent: Color) =
        enqueue(Toast(text, detail?.takeIf { it.isNotBlank() }, ToastKind.INFO, accent, null, emptyList()))

    @Synchronized
    private fun enqueue(toast: Toast) {
        if (toast.text.isBlank()) return
        toasts.addLast(toast)
        // Oldest out first; only marked here, removed after animating away.
        toasts.filterNot { it.dismissed }.dropLast(MAX_SHOWN).forEach { it.dismissed = true }
        // Nothing prunes it while hidden, so cap it.
        while (toasts.size > MAX_QUEUED) toasts.removeFirst()
    }

    @Synchronized
    private fun hasToasts() = toasts.isNotEmpty()

    /** Over an open screen; the HUD pass covers the rest. */
    fun render() {
        if (HudEditorMode.active || !Platform.screenHost.anyScreenOpen) return
        screenPass = true
        try {
            element.renderElement(false)
        } finally {
            screenPass = false
        }
    }

    @Synchronized
    private fun live(): List<Shown> {
        val now = TimeSource.Monotonic.markNow()
        // Never-drawn dismissed notices clear at once.
        toasts.removeAll { it.leaving(now) && !it.reveal.get().isVisible }
        return toasts.map { toast ->
            if (toast.hovered && !toast.dismissed) toast.held += now - toast.lastSeen
            toast.lastSeen = now
            toast.reveal.animateTo(if (toast.leaving(now)) 0f else 1f)
            val card = toast.card ?: layout(toast.text, toast.detail, toast.image, toast.actions).also { toast.card = it }
            Shown(toast, card, toast.kind, toast.accent, toast.reveal.get().coerceIn(0f, 1f), toast.remaining(now).coerceIn(0f, 1f))
        }
    }

    // Editor preview: a short notice and a wrapped one.
    private fun examples(): List<Shown> = listOf(
        Shown(
            null,
            layout(I18n.trOr("blackaddons.chat_copy.copied", "Message copied"), null, null, emptyList()),
            ToastKind.SUCCESS, ToastKind.SUCCESS.color, 1f, 0.35f,
        ),
        Shown(
            null,
            layout(I18n.trOr("blackaddons.backend.unavailable", "BlackAddons servers are unavailable"), "api.blackaddons.com", null, emptyList()),
            ToastKind.ERROR, ToastKind.ERROR.color, 1f, 0.8f,
        ),
    )

    /**
     * Newest nearest the anchored edge, sliding in from it. Cards take space by reveal so others glide aside.
     */
    private fun stack(shown: List<Shown>): LayoutNode {
        val ordered = if (element.anchorY == AnchorY.TOP) shown.asReversed() else shown
        val width = ordered.maxOfOrNull { it.card.width } ?: 0f
        val height = (ordered.sumOf { ((it.card.height + GAP) * it.reveal).toDouble() }.toFloat() - GAP).coerceAtLeast(0f)
        return object : LayoutNode {
            override val width = width
            override val height = height
            override fun draw(x: Float, y: Float) {
                val buttons = ArrayList<Hit>()
                val cards = ArrayList<Hit>()
                val pointer = element.pointer
                var cursor = y
                for (s in ordered) {
                    val hidden = 1f - s.reveal
                    val cardX = x + when (element.anchorX) {
                        AnchorX.LEFT -> -SLIDE * hidden
                        AnchorX.CENTER -> (width - s.card.width) / 2f
                        AnchorX.RIGHT -> width - s.card.width + SLIDE * hidden
                    }
                    val slideY = if (element.anchorX != AnchorX.CENTER) 0f else when (element.anchorY) {
                        AnchorY.TOP -> -SLIDE * hidden
                        AnchorY.MIDDLE -> 0f
                        AnchorY.BOTTOM -> SLIDE * hidden
                    }
                    val cardY = cursor + slideY
                    val body = Hit(cardX, cardY, s.card.width, s.card.height) {}
                    s.toast?.hovered = pointer != null && body.has(pointer.x, pointer.y)
                    drawCard(s, cardX, cardY, buttons)
                    cards += body
                    cursor += (s.card.height + GAP) * s.reveal
                }
                hits = buttons + cards
            }
        }
    }

    private fun drawCard(s: Shown, x: Float, y: Float, buttons: MutableList<Hit>) {
        val w = s.card.width
        val h = s.card.height
        Render.globalAlpha(s.reveal)
        try {
            Render.rect(x, y, w, h, Theme.BACKGROUND.withAlpha(CARD_ALPHA), CARD_RADIUS)
            // Drawn as the whole card cut by a scissor so the corner arcs match.
            cutOut(x, y, w, h, x, y, STRIP, h, s.accent)
            // Drains rather than fills - filling reads as "loading".
            val barW = (w - STRIP) * s.remaining
            cutOut(x, y, w, h, x + w - barW, y + h - BAR, barW, BAR, s.accent.withAlpha(BAR_ALPHA))

            val left = x + STRIP + PAD_X
            val right = x + w - PAD_X
            var ty = y + PAD_Y
            s.image?.let { image ->
                Render.image(image.path, left, ty, right - left, s.card.imageH, Theme.RADIUS_SMALL)
                ty += s.card.imageH + IMAGE_GAP
            }

            val font = HudFontContext.font
            val lineH = Render.textHeight(Theme.FONT_SMALL, titleFont(font))
            Render.icon(s.kind.icon, left, centered(ty, lineH, ICON), ICON, s.accent)

            val textX = left + ICON + ICON_GAP
            for (line in s.card.title) {
                Render.formattedText(line, textX, ty, Theme.FONT_SMALL, titleFont(font), Theme.TEXT_PRIMARY)
                ty += lineH
            }
            if (s.card.detail.isNotEmpty()) ty += DETAIL_GAP
            for (line in s.card.detail) {
                Render.formattedText(line, textX, ty, Theme.FONT_SMALL, font, Theme.TEXT_SECONDARY)
                ty += lineH
            }

            if (s.actions.isEmpty()) return
            ty += ACTIONS_GAP
            val pointer = element.pointer
            var bx = textX
            for ((i, action) in s.actions.withIndex()) {
                val bw = s.card.actionWidths[i]
                val hit = Hit(bx, ty, bw, BUTTON_H) {
                    action.onClick()
                    if (action.dismisses) s.toast?.dismissed = true
                }
                val hovered = pointer != null && hit.has(pointer.x, pointer.y)
                Render.rect(bx, ty, bw, BUTTON_H, if (hovered) Theme.SURFACE_HOVER else Theme.SURFACE, Theme.RADIUS_SMALL)
                val label = action.label()
                Render.text(
                    label, bx + (bw - Render.textWidth(label, Theme.FONT_SMALL, titleFont(font))) / 2f,
                    Render.centerY(ty, BUTTON_H, Theme.FONT_SMALL, titleFont(font)),
                    Theme.FONT_SMALL, if (hovered) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY, titleFont(font),
                )
                buttons += hit
                bx += bw + BUTTON_GAP
            }
        } finally {
            Render.globalAlpha(1f)
        }
    }

    private fun titleFont(font: Font) = if (font.path == Fonts.regular.path) font.copy(path = Fonts.medium.path) else font

    private fun cutOut(x: Float, y: Float, w: Float, h: Float, cx: Float, cy: Float, cw: Float, ch: Float, color: Color) {
        if (cw <= 0f) return
        Render.pushScissor(cx, cy, cw, ch)
        Render.rect(x, y, w, h, color, CARD_RADIUS)
        Render.popScissor()
    }

    private fun layout(text: String, detail: String?, image: ToastImage?, actions: List<ToastAction>): Card {
        val font = HudFontContext.fontOf(element.fontOverride)
        val title = wrap(text, titleFont(font), MAX_TITLE_LINES)
        val body = detail?.let { wrap(it, font, MAX_DETAIL_LINES) }.orEmpty()
        // Fixed at show time, so a later label change doesn't reflow the card.
        val actionWidths = actions.map { Render.textWidth(it.label(), Theme.FONT_SMALL, titleFont(font)) + BUTTON_PAD * 2f }
        val actionsW = if (actions.isEmpty()) 0f else actionWidths.sum() + BUTTON_GAP * (actions.size - 1)
        val textW = maxOf(
            title.maxOf { Render.formattedTextWidth(it, Theme.FONT_SMALL, titleFont(font)) },
            body.maxOfOrNull { Render.formattedTextWidth(it, Theme.FONT_SMALL, font) } ?: 0f,
            actionsW,
        )
        val innerW = maxOf(ICON + ICON_GAP + textW, if (image != null) IMAGE_W else 0f)
        val imageH = if (image != null) innerW / image.aspect.coerceAtLeast(0.1f) else 0f
        val lineH = Render.textHeight(Theme.FONT_SMALL, titleFont(font))
        val textH = (title.size + body.size) * lineH + if (body.isEmpty()) 0f else DETAIL_GAP
        return Card(
            title, body, imageH, actionWidths,
            width = STRIP + PAD_X + innerW + PAD_X,
            height = PAD_Y + (if (image != null) imageH + IMAGE_GAP else 0f) + textH +
                (if (actions.isEmpty()) 0f else ACTIONS_GAP + BUTTON_H) + PAD_Y + BAR,
        )
    }

    /** Up to [maxLines] lines, last one ellipsized. Color codes carry across breaks. */
    private fun wrap(text: String, font: Font, maxLines: Int): List<String> {
        fun fits(s: String) = Render.formattedTextWidth(s, Theme.FONT_SMALL, font) <= MAX_TEXT_WIDTH

        val lines = ArrayList<String>()
        var line = ""
        for (word in text.split(' ')) {
            if (word.isEmpty()) continue
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || fits(candidate)) {
                line = candidate
            } else {
                lines += line
                line = activeCodes(line) + word
            }
        }
        if (line.isNotEmpty()) lines += line
        if (lines.isEmpty()) return listOf("")

        val kept = lines.take(maxLines).toMutableList()
        val cut = lines.size > maxLines
        return kept.mapIndexed { i, l -> if ((cut && i == kept.lastIndex) || !fits(l)) ellipsize(l, ::fits) else l }
    }

    private fun ellipsize(line: String, fits: (String) -> Boolean): String {
        var s = line
        while (s.isNotEmpty() && !fits("$s...")) {
            s = s.dropLast(1)
            // A trailing `§` would eat the ellipsis.
            if (s.endsWith('§')) s = s.dropLast(1)
        }
        return "${s.trimEnd()}..."
    }

    private fun activeCodes(line: String): String {
        var color = ""
        val formats = StringBuilder()
        var i = 0
        while (i < line.length - 1) {
            if (line[i] == '§') {
                when (val code = line[i + 1].lowercaseChar()) {
                    in '0'..'9', in 'a'..'f' -> {
                        color = "§$code"; formats.clear()
                    }
                    in 'k'..'o' -> formats.append('§').append(code)
                    'r' -> {
                        color = ""; formats.clear()
                    }
                }
                i += 2
            } else {
                i++
            }
        }
        return color + formats
    }
}
