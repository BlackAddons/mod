package org.blackaddons.api.ui.render

import org.joml.Matrix3x2fc
import java.awt.Color

enum class GradientDir { LEFT_RIGHT, TOP_BOTTOM }

/**
 * Resource-path font, drawn at [scale] x size, shifted by [shiftX]/[shiftY] ems, with synthetic
 * [bold]/[italic] and [letterSpacing] ems.
 */
data class Font @JvmOverloads constructor(
    val path: String,
    val scale: Float = 1f,
    val shiftX: Float = 0f,
    val shiftY: Float = 0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val letterSpacing: Float = 0f,
)

/** Classpath, file or URL. */
data class Image(val path: String)

/** Recorded once and replayed; see [Render.layer]. Free with [Render.deleteLayer]. */
class RenderLayer

/** One rect of a same-style batch. */
data class BatchRect(val x: Float, val y: Float, val width: Float, val height: Float)

/** The UI drawing backend (BlackSkija, immediate-mode). Draw calls append to the host's per-frame batch. */
interface Renderer {

    /** Host only; reached through `Render.beginPass`. */
    fun beginPass() {}

    // Pair push/pop so changes don't leak.
    fun push()
    fun pop()
    fun translate(x: Number, y: Number)
    fun scale(x: Number, y: Number)
    fun rotate(angle: Number)
    fun transform(matrix: Matrix3x2fc)
    fun globalAlpha(alpha: Number)
    fun pushAntiAlias(enabled: Boolean)
    fun popAntiAlias()

    /** Local point to screen space through the transform stack; `[x, y]`. */
    fun toScreen(x: Number, y: Number): FloatArray

    // A stack; pair push/pop.
    fun pushScissor(x: Number, y: Number, w: Number, h: Number)
    fun popScissor()

    fun line(x1: Number, y1: Number, x2: Number, y2: Number, thickness: Number, color: Color)
    fun rect(x: Number, y: Number, w: Number, h: Number, color: Color, radius: Number = 0f)
    fun rects(rects: List<BatchRect>, color: Color, radius: Number = 0f)
    /** Interleaved `x, y` vertices. */
    fun polygon(points: FloatArray, color: Color, corner: Number = 0f)
    /** [data] can be cleared and reused right after. Free with [deleteShape]. */
    fun createShape(data: PathData): ShapePath
    fun deleteShape(shape: ShapePath)

    /** In the current transform. */
    fun fillShape(shape: ShapePath, color: Color)

    /** Thickness 0 is a one-device-pixel hairline. */
    fun strokeShape(shape: ShapePath, thickness: Number, color: Color)

    /** Replays [layer]'s recording, or runs and records [content] if stale. */
    fun layer(layer: RenderLayer, x: Number, y: Number, w: Number, h: Number, content: () -> Unit)

    /** Next [layer] call re-runs its content. */
    fun invalidateLayer(layer: RenderLayer)

    fun deleteLayer(layer: RenderLayer)

    fun hollowRect(x: Number, y: Number, w: Number, h: Number, thickness: Number, color: Color, radius: Number = 0f)
    fun halfRoundedRect(x: Number, y: Number, w: Number, h: Number, color: Color, radius: Number, roundTop: Boolean)
    fun gradientRect(x: Number, y: Number, w: Number, h: Number, c1: Color, c2: Color, dir: GradientDir, radius: Number = 0f)
    fun circle(x: Number, y: Number, radius: Number, color: Color)
    /** A rounded box grown by [spread] and blurred by [blur]. With a bright [color] it looks like a soft glow. */
    fun dropShadow(x: Number, y: Number, w: Number, h: Number, blur: Number, spread: Number, radius: Number, color: Color = SHADOW)

    /**
     * Fills a rounded rect with an SkSL shader, `half4 main(float2 p)`, where `p` is in the rect's coordinates.
     * [uniforms] are passed by name. Each distinct source is compiled once, so keep [sksl] a constant.
     */
    fun shaderRect(x: Number, y: Number, w: Number, h: Number, sksl: String, uniforms: Map<String, FloatArray>, radius: Number = 0f)
    fun icon(
        data: String, x: Number, y: Number, size: Number, color: Color,
        viewBox: Number = 24, thickness: Number = 2,
    )

    /**
     * ([x], [y]) is the top-left of the ascent box, not the baseline. [size] is an em size; a line is ~1.3x
     * taller - use [textMetrics] or `Render.centerY`.
     */
    fun text(text: String, x: Number, y: Number, size: Number, color: Color, font: Font)
    fun textShadow(text: String, x: Number, y: Number, size: Number, color: Color, font: Font)
    fun textGradient(text: String, x: Number, y: Number, size: Number,
                     c1: Color, c2: Color, font: Font, dir: GradientDir = GradientDir.LEFT_RIGHT)
    fun textWidth(text: String, size: Number, font: Font): Float

    /** `[ascent, descent]` from the baseline, both positive. */
    fun textMetrics(size: Number, font: Font): FloatArray
    fun wrappedText(text: String, x: Number, y: Number, w: Number, size: Number,
                    color: Color, font: Font, lineHeight: Number = 1f)
    /** `[minX, minY, maxX, maxY]`. */
    fun wrappedTextBounds(text: String, w: Number, size: Number, font: Font, lineHeight: Number = 1f): FloatArray

    /** Renderers that can't place pictures draw the text alone. */
    fun richText(spans: List<RichSpan>, x: Number, y: Number, w: Number, size: Number,
                 color: Color, font: Font, lineHeight: Number = 1f) =
        wrappedText(spans.plainText(), x, y, w, size, color, font, lineHeight)

    /** `[minX, minY, maxX, maxY]`. */
    fun richTextBounds(spans: List<RichSpan>, w: Number, size: Number, font: Font, lineHeight: Number = 1f): FloatArray =
        wrappedTextBounds(spans.plainText(), w, size, font, lineHeight)

    // Ref-counted - pair createImage/deleteImage.
    fun createImage(path: String): Image
    fun deleteImage(image: Image)
    fun image(image: Image, x: Number, y: Number, w: Number, h: Number, radius: Number = 0f)
    fun image(path: String, x: Number, y: Number, w: Number, h: Number, radius: Number = 0f)

    /** Own pixels; null while loading or on failure. */
    fun imageSize(path: String): FloatArray?

    /** Draws a source region (texture pixels) of [image] into the rect. Non-null [tint] multiplies texels. */
    fun image(image: Image, x: Number, y: Number, w: Number, h: Number,
              srcX: Number, srcY: Number, srcW: Number, srcH: Number,
              texW: Number, texH: Number, tint: Color? = null, radius: Number = 0f)

    fun lottie(path: String, x: Number, y: Number, w: Number, h: Number)

    /**
     * Item by id (e.g. `minecraft:stone_sword`) through Minecraft's model pipeline (resource packs, glint).
     * Lands one frame late. Unknown ids draw nothing. For captured stacks use
     * [org.blackaddons.api.gui.ScreenDraw.item].
     */
    fun item(id: String, x: Number, y: Number, w: Number, h: Number, radius: Number = 0f, tint: Color? = null)

    /**
     * 3D player-head item from the profile's [textures] (null = default). Any player works; the skin loads
     * async, so the default head shows first.
     */
    fun playerHead(uuid: String, name: String, textures: String?,
                   x: Number, y: Number, w: Number, h: Number, radius: Number = 0f)

    /** Online players only. */
    fun playerFace(name: String, x: Number, y: Number, w: Number, h: Number, showHat: Boolean = true, radius: Number = 0f): Boolean

    /**
     * Item from modern `{ "id", "count", "components" }` JSON, same pipeline as [item]. Draws nothing on
     * malformed JSON or outside a world.
     */
    fun itemSpec(json: String, x: Number, y: Number, w: Number, h: Number, radius: Number = 0f)

    companion object {
        val SHADOW = Color(0, 0, 0, 128)
    }
}

private fun List<RichSpan>.plainText(): String = joinToString("") { (it as? RichSpan.Text)?.text ?: "" }
