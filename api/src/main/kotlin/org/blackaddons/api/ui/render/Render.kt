package org.blackaddons.api.ui.render

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
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
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.GlassCheck
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.Chroma
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.api.util.stripFormatting
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import java.awt.Color
import kotlin.math.hypot
import kotlin.math.min

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

    /** A [Glass] card, eased toward its hover look by [hover] (0..1). */
    fun glass(x: Number, y: Number, w: Number, h: Number, style: Glass, hover: Float = 0f) {
        rect(x, y, w, h, Color(style.fill(), true), style.radius)
        val wash = style.wash(hover)
        if (wash ushr 24 != 0) rect(x, y, w, h, Color(wash, true), style.radius)
        if (style.hasBorder) hollowRect(x, y, w, h, style.borderWidth, Color(style.border(hover), true), style.radius)
    }

    /** A [GlassCheck] box, [on] eased 0..1 (the mark draws on with it). */
    fun checkbox(x: Float, y: Float, size: Float, on: Float) {
        val r = GlassCheck.RADIUS
        if (on > 0f) hollowRect(x - 1f, y - 1f, size + 2f, size + 2f, 1f, Color(GlassCheck.glow(on), true), r + 1f)
        rect(x, y, size, size, Color(GlassCheck.base(), true), r)
        rect(x, y, size, size, Color(GlassCheck.frost(on, 0f), true), r)
        rect(x, y, size, size, Color(GlassCheck.accent(on), true), r)
        hollowRect(x, y, size, size, 1f, Color(GlassCheck.edge(on, 0f), true), r)
        if (on <= 0f) return
        val sx = x + GlassCheck.START_X * size
        val sy = y + GlassCheck.START_Y * size
        val ex = x + GlassCheck.ELBOW_X * size
        val ey = y + GlassCheck.ELBOW_Y * size
        val fx = x + GlassCheck.END_X * size
        val fy = y + GlassCheck.END_Y * size
        val first = hypot(ex - sx, ey - sy)
        val drawn = on * (first + hypot(fx - ex, fy - ey))
        val mark = Color(GlassCheck.mark(), true)
        val stroke = size * GlassCheck.STROKE
        val t1 = min(1f, drawn / first)
        line(sx, sy, sx + (ex - sx) * t1, sy + (ey - sy) * t1, stroke, mark)
        if (drawn <= first) return
        val t2 = (drawn - first) / hypot(fx - ex, fy - ey)
        line(ex, ey, ex + (fx - ex) * t2, ey + (fy - ey) * t2, stroke, mark)
    }

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
    fun ellipsize(text: String, maxWidth: Number, size: Number, font: Font): String = TextFit.ellipsize(text, maxWidth, size, font)

    /** [ellipsize] for text drawn some other way than one plain run, with [measure] giving its drawn width. */
    fun ellipsize(text: String, maxWidth: Number, measure: (String) -> Float): String = TextFit.ellipsize(text, maxWidth, measure)

    fun textOverflows(text: String, maxWidth: Number, size: Number, font: Font): Boolean =
        textWidth(text, size, font) > maxWidth.toFloat()

    /**
     * Wraps at spaces and breaks at `\n`. Words that are too long break between characters. Always returns at least one
     * line.
     */
    fun wrapText(text: String, maxWidth: Number, size: Number, font: Font): List<String> = TextFit.wrapText(text, maxWidth, size, font)

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
    ): Float = TextEffects.chromaText(text, x, y, size, base, speed, font, angleDeg, scale)

    fun chromaText(
        text: String, x: Number, y: Number, size: Number,
        color: ColorSetting, font: Font,
        angleDeg: Float = Chroma.DEFAULT_ANGLE,
        scale: Float = Chroma.DEFAULT_SPATIAL,
    ): Float = TextEffects.chromaText(text, x, y, size, color, font, angleDeg, scale)

    /**
     * Per-character gradient. [animated] flows it, [solid] cycles the whole string as one color. Parse CSS
     * into [stops] with [org.blackaddons.api.util.color.GradientParser]. Returns drawn width.
     */
    fun gradientText(
        text: String, x: Number, y: Number, size: Number,
        stops: List<ColorStop>, font: Font,
        animated: Boolean = false, speed: Float = 1f, solid: Boolean = false,
    ): Float = TextEffects.gradientText(text, x, y, size, stops, font, animated, speed, solid)

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
