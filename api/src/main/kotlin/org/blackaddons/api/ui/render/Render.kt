package org.blackaddons.api.ui.render

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.feature.settings.impl.ChromaMode
import org.blackaddons.api.feature.settings.impl.ColorSetting
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render.ellipsize
import org.blackaddons.api.ui.render.Render.hollowRect
import org.blackaddons.api.ui.render.Render.invalidateLayer
import org.blackaddons.api.ui.render.Render.layer
import org.blackaddons.api.ui.render.Render.popScissor
import org.blackaddons.api.ui.render.Render.rect
import org.blackaddons.api.ui.render.Render.toggleSwitch
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.Chroma
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.GradientColors
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.api.util.graphemes
import org.blackaddons.api.util.stripFormatting
import org.blackaddons.api.util.takeValidUtf16
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/** Entry point for UI drawing. Resolves [Platform.renderer] per call; immediate-mode, so there's no frame to open. */
object Render {

    private inline val r: Renderer get() = Platform.renderer

    /**
     * Host only. Resets pushes/scissors a previous pass left open, so one missing [popScissor] can't clip
     * clicks for the rest of the session.
     */
    fun beginPass() {
        pushes = 0
        antiAliases = 0
        clips.clear()
        r.beginPass()
    }

    /** Runs [block]; on a throw closes every push, scissor and anti-alias it left open, then rethrows. */
    fun <T> isolated(block: () -> T): T {
        val pushesBefore = pushes
        val clipsBefore = clips.size
        val antiAliasesBefore = antiAliases
        try {
            return block()
        } catch (t: Throwable) {
            // Order doesn't matter: each push/scissor is one canvas save, alpha has its own stack.
            while (clips.size > clipsBefore) popScissor()
            while (pushes > pushesBefore) pop()
            while (antiAliases > antiAliasesBefore) popAntiAlias()
            throw t
        }
    }

    // Counted here because the backend only records draws.
    private var pushes = 0
    private var antiAliases = 0

    fun push() {
        r.push()
        pushes++
    }

    fun pop() {
        r.pop()
        if (pushes > 0) pushes--
    }
    fun translate(x: Number, y: Number) = r.translate(x, y)
    fun scale(x: Number, y: Number) = r.scale(x, y)
    fun rotate(angle: Number) = r.rotate(angle)
    fun shearX(amount: Number) = transform(Matrix3x2f().shearX(amount.toFloat()))
    fun transform(matrix: Matrix3x2fc) = r.transform(matrix)
    fun globalAlpha(alpha: Number) = r.globalAlpha(alpha)
    fun pushAntiAlias(enabled: Boolean) {
        r.pushAntiAlias(enabled)
        antiAliases++
    }

    fun popAntiAlias() {
        r.popAntiAlias()
        if (antiAliases > 0) antiAliases--
    }

    fun <T> withAntiAlias(enabled: Boolean, block: () -> T): T {
        pushAntiAlias(enabled)
        return try {
            block()
        } finally {
            popAntiAlias()
        }
    }

    fun toScreen(x: Number, y: Number): FloatArray = r.toScreen(x, y)

    fun pushScissor(x: Number, y: Number, w: Number, h: Number) {
        r.pushScissor(x, y, w, h)
        val outer = clips.lastOrNull()
        val x1 = x.toFloat()
        val y1 = y.toFloat()
        val box = floatArrayOf(x1, y1, x1 + w.toFloat(), y1 + h.toFloat())
        if (outer != null) {
            box[0] = maxOf(box[0], outer[0]); box[1] = maxOf(box[1], outer[1])
            box[2] = minOf(box[2], outer[2]); box[3] = minOf(box[3], outer[3])
        }
        clips.addLast(box)
    }

    fun popScissor() {
        r.popScissor()
        clips.removeLastOrNull()
    }

    // Scissor areas, innermost last; click regions are cut to these.
    private val clips = ArrayDeque<FloatArray>()

    /** `[x1, y1, x2, y2]`, or null when nothing clips. */
    internal val clip: FloatArray? get() = clips.lastOrNull()

    fun line(x1: Number, y1: Number, x2: Number, y2: Number, thickness: Number, color: Color) =
        r.line(x1, y1, x2, y2, thickness, color)

    fun rect(x: Number, y: Number, w: Number, h: Number, color: Color, radius: Number = 0f) =
        r.rect(x, y, w, h, color, radius)

    fun rects(rects: List<BatchRect>, color: Color, radius: Number = 0f) = r.rects(rects, color, radius)

    fun polygon(points: FloatArray, color: Color, corner: Number = 0f) = r.polygon(points, color, corner)

    fun createShape(data: PathData): ShapePath = r.createShape(data)
    fun deleteShape(shape: ShapePath) = r.deleteShape(shape)
    fun fillShape(shape: ShapePath, color: Color) = r.fillShape(shape, color)
    fun strokeShape(shape: ShapePath, thickness: Number, color: Color) = r.strokeShape(shape, thickness, color)

    /**
     * [content] only runs while [layer] is stale; otherwise the recording replays, so [invalidateLayer] on
     * change. Content must only draw: stamping a `Rect` throws, hover/animations freeze, no items or game
     * textures.
     */
    fun layer(layer: RenderLayer, x: Number, y: Number, w: Number, h: Number, content: () -> Unit) =
        r.layer(layer, x, y, w, h) {
            layerDepth++
            try {
                content()
            } finally {
                layerDepth--
            }
        }

    private var layerDepth = 0

    internal val recordingLayer: Boolean get() = layerDepth > 0

    fun invalidateLayer(layer: RenderLayer) = r.invalidateLayer(layer)
    fun deleteLayer(layer: RenderLayer) = r.deleteLayer(layer)

    /** [rect] over a laid-out box. */
    fun rect(box: Rect, color: Color, radius: Number = 0f) = rect(box.x, box.y, box.w, box.h, color, radius)

    /** [hollowRect] over a laid-out box. */
    fun hollowRect(box: Rect, thickness: Number, color: Color, radius: Number = 0f) =
        hollowRect(box.x, box.y, box.w, box.h, thickness, color, radius)

    /**
     * An outline drawn wholly inside the box, so a scissor at the box or a fill next to it can't cut half of it off.
     * To ring a box from outside, grow the box by [thickness] on each side.
     */
    fun hollowRect(x: Number, y: Number, w: Number, h: Number, thickness: Number, color: Color, radius: Number = 0f) {
        val t = thickness.toFloat()
        val half = t / 2f
        r.hollowRect(
            x.toFloat() + half, y.toFloat() + half, (w.toFloat() - t).coerceAtLeast(0f), (h.toFloat() - t).coerceAtLeast(0f),
            t, color, (radius.toFloat() - half).coerceAtLeast(0f),
        )
    }

    fun halfRoundedRect(x: Number, y: Number, w: Number, h: Number, color: Color, radius: Number, roundTop: Boolean) =
        r.halfRoundedRect(x, y, w, h, color, radius, roundTop)

    fun gradientRect(x: Number, y: Number, w: Number, h: Number, c1: Color, c2: Color, dir: GradientDir, radius: Number = 0f) =
        r.gradientRect(x, y, w, h, c1, c2, dir, radius)

    fun circle(x: Number, y: Number, radius: Number, color: Color) = r.circle(x, y, radius, color)

    fun lottie(path: String, x: Number, y: Number, w: Number, h: Number) = r.lottie(path, x, y, w, h)

    fun icon(
        data: String, x: Number, y: Number, size: Number, color: Color,
        viewBox: Number = 24, thickness: Number = 2,
    ) = r.icon(data, x, y, size, color, viewBox, thickness)

    /** Includes any solid details over the outline. */
    fun icon(icon: VectorIcon, x: Number, y: Number, size: Number, color: Color) {
        icon(icon.strokeData, x, y, size, color, icon.viewBox, icon.thickness)
        icon.fillData?.let { icon(it, x, y, size, color, icon.viewBox, thickness = 0) }
    }

    fun dropShadow(
        x: Number, y: Number, w: Number, h: Number, blur: Number, spread: Number, radius: Number,
        color: Color = Renderer.SHADOW,
    ) = r.dropShadow(x, y, w, h, blur, spread, radius, color)

    fun shaderRect(x: Number, y: Number, w: Number, h: Number, sksl: String, uniforms: Map<String, FloatArray>, radius: Number = 0f) =
        r.shaderRect(x, y, w, h, sksl, uniforms, radius)

    fun text(text: String, x: Number, y: Number, size: Number, color: Color, font: Font) =
        r.text(text, x, y, size, color, font)

    fun textShadow(text: String, x: Number, y: Number, size: Number, color: Color, font: Font) =
        r.textShadow(text, x, y, size, color, font)

    fun textGradient(text: String, x: Number, y: Number, size: Number,
                     c1: Color, c2: Color, font: Font, dir: GradientDir = GradientDir.LEFT_RIGHT) =
        r.textGradient(text, x, y, size, c1, c2, font, dir)

    fun textWidth(text: String, size: Number, font: Font): Float = r.textWidth(text, size, font)

    /** `[ascent, descent]`, both positive. */
    fun textMetrics(size: Number, font: Font): FloatArray = r.textMetrics(size, font)

    fun textCentered(text: String, cx: Number, y: Number, size: Number, color: Color, font: Font) =
        text(text, cx.toFloat() - textWidth(text, size, font) / 2f, y, size, color, font)

    /**
     * Cut to [maxWidth] with an ellipsis; hovering cut text shows all of it in a [Tooltip]. Returns the drawn width.
     */
    fun text(text: String, x: Number, y: Number, size: Number, color: Color, font: Font, maxWidth: Number): Float =
        fitted(text, x.toFloat(), y.toFloat(), size, color, font, maxWidth, Align.LEFT)

    /** [text] with a [maxWidth], ending at [right]. */
    fun textRight(text: String, right: Number, y: Number, size: Number, color: Color, font: Font, maxWidth: Number): Float =
        fitted(text, right.toFloat(), y.toFloat(), size, color, font, maxWidth, Align.RIGHT)

    /** [text] with a [maxWidth], centered on [cx]. */
    fun textCentered(text: String, cx: Number, y: Number, size: Number, color: Color, font: Font, maxWidth: Number): Float =
        fitted(text, cx.toFloat(), y.toFloat(), size, color, font, maxWidth, Align.CENTER)

    private enum class Align { LEFT, RIGHT, CENTER }

    private fun fitted(text: String, anchor: Float, y: Float, size: Number, color: Color, font: Font, maxWidth: Number, align: Align): Float {
        val shown = ellipsize(text, maxWidth, size, font)
        val w = textWidth(shown, size, font)
        val x = when (align) {
            Align.LEFT -> anchor
            Align.RIGHT -> anchor - w
            Align.CENTER -> anchor - w / 2f
        }
        text(shown, x, y, size, color, font)
        if (shown != text) Tooltip.showOver(x, y, w, textHeight(size, font), text)
        return w
    }

    fun textCenteredShadow(text: String, cx: Number, y: Number, size: Number, color: Color, font: Font) =
        textShadow(text, cx.toFloat() - textWidth(text, size, font) / 2f, y, size, color, font)

    /** About 1.3x [size], not [size]. */
    fun textHeight(size: Number, font: Font): Float = textMetrics(size, font).let { it[0] + it[1] }

    /** With a [maxWidth], cut text ends in an ellipsis and shows all of it in a [Tooltip] on hover. */
    fun text(text: String, x: Number, y: Number, style: TextStyle, maxWidth: Number? = null) {
        if (maxWidth == null) text(text, x, y, style.size, style.color, style.font)
        else text(text, x, y, style.size, style.color, style.font, maxWidth)
    }

    fun textRight(text: String, right: Number, y: Number, style: TextStyle) =
        text(text, right.toFloat() - textWidth(text, style), y, style)

    fun textRight(text: String, right: Number, y: Number, style: TextStyle, maxWidth: Number) {
        textRight(text, right, y, style.size, style.color, style.font, maxWidth)
    }

    fun textCentered(text: String, cx: Number, y: Number, style: TextStyle) =
        textCentered(text, cx, y, style.size, style.color, style.font)

    fun textCentered(text: String, cx: Number, y: Number, style: TextStyle, maxWidth: Number) {
        textCentered(text, cx, y, style.size, style.color, style.font, maxWidth)
    }

    fun textWidth(text: String, style: TextStyle): Float = textWidth(text, style.size, style.font)

    fun ellipsize(text: String, maxWidth: Number, style: TextStyle): String = ellipsize(text, maxWidth, style.size, style.font)

    fun centerY(top: Number, height: Number, style: TextStyle): Float = centerY(top, height, style.size, style.font)

    /**
     * Ellipsizes [text] to fit [maxWidth] (just the ellipsis if nothing fits). For drawing, pass `maxWidth` to [text]
     * instead: it also shows the cut part on hover.
     */
    fun ellipsize(text: String, maxWidth: Number, size: Number, font: Font): String {
        val max = maxWidth.toFloat()
        if (textWidth(text, size, font) <= max) return text
        /*
         * The search probes are new layouts, so a cut drawn every frame is remembered. Re-measuring the result
         * (a cached layout) catches a font that changed under it.
         */
        val key = Cut(text, max, size.toFloat(), font)
        cuts[key]?.takeIf { textWidth(it, size, font) <= max }?.let { return it }
        return ellipsize(text, max) { textWidth(it, size, font) }.also { cuts[key] = it }
    }

    private data class Cut(val text: String, val maxWidth: Float, val size: Float, val font: Font)

    private val cuts = object : LinkedHashMap<Cut, String>(CUT_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Cut, String>) = size > CUT_CACHE
    }

    private const val CUT_CACHE = 512

    /** [ellipsize] for text drawn some other way than one plain run, with [measure] giving its drawn width. */
    fun ellipsize(text: String, maxWidth: Number, measure: (String) -> Float): String {
        val max = maxWidth.toFloat()
        if (measure(text) <= max) return text
        // Binary search: each probe is a text layout, and a linear walk floods the layout cache.
        var low = 0
        var high = text.length - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            val candidate = text.takeValidUtf16(mid) + ELLIPSIS
            if (measure(candidate) <= max) low = mid else high = mid - 1
        }
        val resultText = text.takeValidUtf16(low)
        return if (resultText.isEmpty()) ELLIPSIS else resultText + ELLIPSIS
    }

    fun textOverflows(text: String, maxWidth: Number, size: Number, font: Font): Boolean =
        textWidth(text, size, font) > maxWidth.toFloat()

    /**
     * Wraps at spaces and breaks at `\n`. Words that are too long break between characters. Always returns at least one
     * line.
     */
    fun wrapText(text: String, maxWidth: Number, size: Number, font: Font): List<String> {
        if ('\n' in text) return text.split('\n').flatMap { wrapText(it, maxWidth, size, font) }
        val limit = maxWidth.toFloat()
        if (text.isEmpty() || limit <= 0f) return listOf(text)

        val lines = ArrayList<String>()
        var line = ""

        for (rawWord in text.split(' ')) {
            if (rawWord.isEmpty()) continue
            var word = rawWord
            // Chop words that can't fit on their own line.
            while (word.length > 1 && textWidth(word, size, font) > limit) {
                if (line.isNotEmpty()) { lines += line; line = "" }
                val clusters = word.graphemes()
                var subLen = 0
                for (g in clusters) {
                    val nextLen = subLen + g.length
                    if (textWidth(word.substring(0, nextLen), size, font) <= limit) {
                        subLen = nextLen
                    } else {
                        break
                    }
                }
                if (subLen == 0 && clusters.isNotEmpty()) {
                    subLen = clusters[0].length
                }
                lines += word.substring(0, subLen)
                word = word.substring(subLen)
            }
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || textWidth(candidate, size, font) <= limit) {
                line = candidate
            } else {
                lines += line
                line = word
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines.ifEmpty { listOf("") }
    }

    private const val ELLIPSIS = "..."

    /**
     * `y` that vertically centers text in the box. Don't use `top + (height - size) / 2` - [size] is an em
     * size, so that sits ~0.15 * size too low.
     */
    fun centerY(top: Number, height: Number, size: Number, font: Font): Float =
        top.toFloat() + (height.toFloat() - textHeight(size, font)) / 2f

    fun chromaText(
        text: String, x: Number, y: Number, size: Number,
        base: Color, speed: Float, font: Font,
        angleDeg: Float = Chroma.DEFAULT_ANGLE,
        scale: Float = Chroma.DEFAULT_SPATIAL,
    ): Float {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dirX = cos(rad).toFloat()
        val dirY = sin(rad).toFloat()
        val startX = x.toFloat()
        var cx = startX
        for (s in text.graphemes()) {
            val screen = r.toScreen(cx, y)
            val offset = (screen[0] * dirX + screen[1] * dirY) * scale
            r.text(s, cx, y, size, Chroma.color(base, speed, offset), font)
            cx += r.textWidth(s, size, font)
        }
        return cx - startX
    }

    fun chromaText(
        text: String, x: Number, y: Number, size: Number,
        color: ColorSetting, font: Font,
        angleDeg: Float = Chroma.DEFAULT_ANGLE,
        scale: Float = Chroma.DEFAULT_SPATIAL,
    ): Float {
        if (!color.chroma || color.chromaMode == ChromaMode.ALL) {
            r.text(text, x, y, size, color.value, font)
            return r.textWidth(text, size, font)
        }
        return chromaText(text, x, y, size, color.baseColor, color.chromaSpeed, font, angleDeg, scale)
    }

    /**
     * Per-character gradient. [animated] flows it, [solid] cycles the whole string as one color. Parse CSS
     * into [stops] with [org.blackaddons.api.util.color.GradientParser]. Returns drawn width.
     */
    fun gradientText(
        text: String, x: Number, y: Number, size: Number,
        stops: List<ColorStop>, font: Font,
        animated: Boolean = false, speed: Float = 1f, solid: Boolean = false,
    ): Float {
        if (stops.isEmpty()) {
            r.text(text, x, y, size, Color.WHITE, font)
            return r.textWidth(text, size, font)
        }
        val glyphs = text.graphemes()
        val n = glyphs.size
        val time = System.currentTimeMillis() / 1000.0
        val startX = x.toFloat()
        var cx = startX
        for (i in glyphs.indices) {
            val s = glyphs[i]
            val rgb = if (animated) {
                // Reversed phase so the wave flows left to right; solid or single glyph pulses in unison.
                val offset = if (solid || n <= 1) 0f else (n - 1 - i).toFloat() / (n - 1)
                GradientColors.animatedGradientColor(stops, speed, offset, time)
            } else {
                val frac = if (solid || n <= 1) 0f else i.toFloat() / (n - 1)
                GradientColors.colorAt(stops, frac)
            }
            r.text(s, cx, y, size, Color((0xFF shl 24) or (rgb and 0xFFFFFF), true), font)
            cx += r.textWidth(s, size, font)
        }
        return cx - startX
    }

    /** Draws `§`/`&`-coded text. Returns drawn width. */
    fun formattedText(
        text: String,
        x: Number,
        y: Number,
        size: Number,
        font: Font,
        defaultColor: Color = Color.WHITE,
    ): Float {
        val message = ChatMessage.legacy(text)
        var currentX = x.toFloat()
        for (span in message.spans) {
            val color = span.color ?: defaultColor
            r.text(span.text, currentX, y, size, color, font)
            currentX += r.textWidth(span.text, size, font)
        }
        return currentX - x.toFloat()
    }

    fun formattedTextWidth(text: String, size: Number, font: Font): Float =
        r.textWidth(text.stripFormatting(), size, font)

    fun wrappedText(text: String, x: Number, y: Number, w: Number, size: Number,
                    color: Color, font: Font, lineHeight: Number = 1f) =
        r.wrappedText(text, x, y, w, size, color, font, lineHeight)

    fun wrappedText(text: String, x: Number, y: Number, w: Number, style: TextStyle, lineHeight: Number = 1f) =
        wrappedText(text, x, y, w, style.size, style.color, style.font, lineHeight)

    /** `[x, y, width, height]`. */
    fun wrappedTextBounds(text: String, w: Number, size: Number, font: Font, lineHeight: Number = 1f): FloatArray =
        r.wrappedTextBounds(text, w, size, font, lineHeight)

    fun wrappedTextHeight(text: String, w: Number, size: Number, font: Font, lineHeight: Number = 1f): Float =
        wrappedTextBounds(text, w, size, font, lineHeight)[3]

    /** Wrapped text with inline pictures (emoji). */
    fun richText(spans: List<RichSpan>, x: Number, y: Number, w: Number, size: Number,
                 color: Color, font: Font, lineHeight: Number = 1f) =
        r.richText(spans, x, y, w, size, color, font, lineHeight)

    fun richText(spans: List<RichSpan>, x: Number, y: Number, w: Number, style: TextStyle, lineHeight: Number = 1f) =
        richText(spans, x, y, w, style.size, style.color, style.font, lineHeight)

    fun richTextBounds(spans: List<RichSpan>, w: Number, size: Number, font: Font, lineHeight: Number = 1f): FloatArray =
        r.richTextBounds(spans, w, size, font, lineHeight)

    /**
     * Prefix for local file image paths. A scheme because classpath and absolute paths look the same outside
     * Windows. `http(s)://` paths are fetched off the render thread.
     */
    const val FILE_SCHEME = "file:"

    fun createImage(path: String): Image = r.createImage(path)
    fun deleteImage(image: Image) = r.deleteImage(image)
    fun image(image: Image, x: Number, y: Number, w: Number, h: Number, radius: Number = 0f) =
        r.image(image, x, y, w, h, radius)
    fun image(path: String, x: Number, y: Number, w: Number, h: Number, radius: Number = 0f) =
        r.image(path, x, y, w, h, radius)
    fun imageSize(path: String): FloatArray? = r.imageSize(path)
    fun image(image: Image, x: Number, y: Number, w: Number, h: Number,
              srcX: Number, srcY: Number, srcW: Number, srcH: Number,
              texW: Number, texH: Number, tint: Color? = null, radius: Number = 0f) =
        r.image(image, x, y, w, h, srcX, srcY, srcW, srcH, texW, texH, tint, radius)

    fun item(id: String, x: Number, y: Number, w: Number, h: Number, radius: Number = 0f, tint: Color? = null) =
        r.item(id, x, y, w, h, radius, tint)

    fun playerHead(uuid: String, name: String, textures: String?,
                   x: Number, y: Number, w: Number, h: Number, radius: Number = 0f) =
        r.playerHead(uuid, name, textures, x, y, w, h, radius)

    fun playerFace(name: String, x: Number, y: Number, w: Number, h: Number, showHat: Boolean = true, radius: Number = 0f): Boolean =
        r.playerFace(name, x, y, w, h, showHat, radius)

    fun itemSpec(json: String, x: Number, y: Number, w: Number, h: Number, radius: Number = 0f) =
        r.itemSpec(json, x, y, w, h, radius)

    /** [toggleSwitch] filling a laid-out box. */
    fun toggleSwitch(box: Rect, t: Float, on: Color = Theme.ACCENT, off: Color = Theme.SURFACE_ACTIVE) =
        toggleSwitch(box.x, box.y, box.w, box.h, t, on, off)

    /** [t] = how far on; [on]/[off] are track colors. */
    fun toggleSwitch(
        x: Float, y: Float, w: Float, h: Float, t: Float,
        on: Color = Theme.ACCENT, off: Color = Theme.SURFACE_ACTIVE,
    ) {
        val pad = 2f
        val thumb = h - pad * 2f
        val trackColor = lerpColor(off, on, t)
        rect(x, y, w, h, trackColor, h / 2f)
        val thumbX = x + pad + (w - h) * t
        rect(thumbX, y + pad, thumb, thumb, Theme.TEXT_PRIMARY, thumb / 2f)
    }

    fun slider(
        x: Float, y: Float, w: Float, h: Float, t: Float,
        active: Boolean = true,
        activeColor: Color = Theme.ACCENT,
        trackColor: Color = Theme.SURFACE_HOVER,
    ) {
        val radius = h / 2f
        rect(x, y, w, h, trackColor, radius)
        val fill = if (active) activeColor else activeColor.withAlpha(90)
        rect(x, y, w * t, h, fill, radius)

        val thumbR = h
        val thumbCx = x + w * t
        val thumbCy = y + h / 2f
        circle(thumbCx, thumbCy, thumbR + 1f, Color(0, 0, 0, 60))
        circle(thumbCx, thumbCy, thumbR, if (active) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY)
    }
}
