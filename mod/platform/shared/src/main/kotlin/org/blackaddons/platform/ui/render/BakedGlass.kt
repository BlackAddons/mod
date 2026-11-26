package org.blackaddons.platform.ui.render

import com.mojang.blaze3d.platform.NativeImage
import io.github.humbleui.skija.*
import io.github.humbleui.types.RRect
import io.github.humbleui.types.Rect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.GlassCheck
import org.blackaddons.api.util.color.fade
import org.blackaddons.core.log.Log
import org.blackaddons.platform.util.modId
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * [Glass] drawn by vanilla's GUI batcher instead of a Skija layer: Skia rasterizes white coverage masks once per shape,
 * and every draw is a vertex-tinted blit. It lands in vanilla's own draw order (under the text and items that follow
 * it, in the HUD, tooltips and screens alike) and costs no composite pass. Theme changes and hover easing only change
 * the tint, so nothing rebakes for them. GUI units, render thread only.
 */
object BakedGlass {

    /** [alpha] fades the whole style, for open animations; [fill] replaces the style's own fill color. */
    fun draw(
        g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, style: Glass,
        hover: Float = 0f, alpha: Float = 1f, fill: Int = style.fill(),
    ) {
        if (w <= 0 || h <= 0) return
        val bordered = style.hasBorder
        val mask = mask(w, h, style.radius, if (bordered) style.borderWidth else 0f)
        paint(g, mask, x, y, w, h, fade(fill, alpha), border = false)
        val wash = fade(style.wash(hover), alpha)
        if (wash ushr 24 != 0) paint(g, mask, x, y, w, h, wash, border = false)
        if (bordered) paint(g, mask, x, y, w, h, fade(style.border(hover), alpha), border = true)
    }

    /** A rounded fill in any color, for shapes that are not a [Glass] (bars, pills). */
    fun fill(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Float, argb: Int) {
        if (w > 0 && h > 0 && argb ushr 24 != 0) paint(g, mask(w, h, radius, 0f), x, y, w, h, argb, border = false)
    }

    /** A rounded outline inside the box, like `Render.hollowRect`. */
    fun border(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Float, width: Float, argb: Int) {
        if (w > 0 && h > 0 && argb ushr 24 != 0) paint(g, mask(w, h, radius, width), x, y, w, h, argb, border = true)
    }

    /** The [GlassCheck] mark filling a [size] box. */
    fun check(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, argb: Int) =
        square(g, x, y, size, argb, key(CHECK, 0f, size)) { paint, px ->
            paint.setMode(PaintMode.STROKE)
            paint.setStrokeWidth(px * GlassCheck.STROKE)
            paint.setStrokeCap(PaintStrokeCap.ROUND)
            paint.setStrokeJoin(PaintStrokeJoin.ROUND)
            val start = floatArrayOf(GlassCheck.START_X, GlassCheck.START_Y, GlassCheck.ELBOW_X, GlassCheck.ELBOW_Y)
            val end = floatArrayOf(GlassCheck.ELBOW_X, GlassCheck.ELBOW_Y, GlassCheck.END_X, GlassCheck.END_Y)
            drawLines(FloatArray(8) { (if (it < 4) start[it] else end[it - 4]) * px }, paint)
        }

    enum class Corner(val right: Boolean, val bottom: Boolean) {
        TOP_LEFT(false, false), TOP_RIGHT(true, false), BOTTOM_LEFT(false, true), BOTTOM_RIGHT(true, true)
    }

    /** What a [radius] [corner] of a rounded box leaves out, as a square there: filled in, it squares that corner off. */
    fun cornerFill(g: GuiGraphicsExtractor, x: Int, y: Int, radius: Float, corner: Corner, argb: Int) {
        val size = ceil(radius).toInt()
        square(g, x, y, size, argb, key(CORNER + corner.ordinal, radius, size)) { paint, px ->
            drawRect(Rect.makeWH(px.toFloat(), px.toFloat()), paint)
            val r = radius * bakedScale
            paint.setBlendMode(BlendMode.CLEAR)
            drawCircle(if (corner.right) px - r else r, if (corner.bottom) px - r else r, r, paint)
        }
    }

    private fun key(kind: Int, radius: Float, size: Int): Long =
        (kind.toLong() shl 48) or ((radius.toRawBits().toLong() and 0xFFFFFFFFL) shl 16) or size.toLong()

    private inline fun square(
        g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, argb: Int, key: Long, draw: Canvas.(Paint, Int) -> Unit,
    ) {
        if (size <= 0 || argb ushr 24 == 0) return
        rescale()
        val px = size * bakedScale
        val id = if (key in squares) squares[key] else bakeSquare(px) { paint -> draw(paint, px) }.also { squares[key] = it }
        if (id != null) g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size, px, px, px, px, argb)
    }

    private inline fun bakeSquare(px: Int, draw: Canvas.(Paint) -> Unit): Identifier? {
        val coverage = try {
            val info = ImageInfo.makeA8(px, px)
            Bitmap().use { bitmap ->
                bitmap.allocPixels(info)
                Canvas(bitmap).use { canvas ->
                    canvas.clear(0)
                    Paint().use { paint ->
                        paint.setAntiAlias(true)
                        paint.setColor(-1)
                        canvas.draw(paint)
                    }
                }
                bitmap.readPixels(info, px.toLong(), 0, 0) ?: error("Square mask readback failed")
            }
        } catch (t: Throwable) {
            Log.main.warn("Square glass mask unavailable", t)
            return null
        }
        val image = NativeImage(px, px, false)
        for (i in 0 until px * px) image.setPixelABGR(i % px, i / px, ((coverage[i].toInt() and 0xFF) shl 24) or 0xFFFFFF)
        val id = modId("glass/${nextId++}")
        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "blackaddons-glass" }, image))
        return id
    }

    private class Mask(
        val radiusPx: Float,
        val borderPx: Float,
        val wholeW: Int,
        val wholeH: Int,
        val corner: Int,
        val cornerPx: Int,
        val shapeW: Int,
        val texW: Int,
        val texH: Int,
        val id: Identifier?,
    ) {
        var lastUsed = 0L
    }

    private val masks = ArrayList<Mask>()
    private val squares = HashMap<Long, Identifier?>()
    private var bakedScale = 0
    private var uses = 0L
    private var nextId = 0
    private var failedAt: TimeSource.Monotonic.ValueTimeMark? = null

    private fun rescale() {
        val scale = Minecraft.getInstance().window.guiScale
        if (scale != bakedScale) {
            clear()
            bakedScale = scale
        }
    }

    private fun mask(w: Int, h: Int, radius: Float, border: Float): Mask {
        rescale()
        val scale = bakedScale
        val radiusPx = min(radius * scale, min(w, h) * scale / 2f)
        val borderPx = border * scale
        val corner = max(1, ceil(max(radiusPx, borderPx) / scale).toInt())
        val whole = (w == h && w <= WHOLE_MAX) || corner * 2 > min(w, h)
        val wholeW = if (whole) w else 0
        val wholeH = if (whole) h else 0
        uses++
        for (m in masks) {
            if (m.radiusPx == radiusPx && m.borderPx == borderPx && m.wholeW == wholeW && m.wholeH == wholeH) {
                m.lastUsed = uses
                return m
            }
        }
        if (masks.size >= MAX_MASKS) masks.minBy(Mask::lastUsed).let { release(it); masks.remove(it) }
        val baked = bake(radiusPx, borderPx, wholeW, wholeH, corner, scale)
        if (baked.id != null) {
            baked.lastUsed = uses
            masks += baked
        }
        return baked
    }

    private fun bake(radiusPx: Float, borderPx: Float, wholeW: Int, wholeH: Int, corner: Int, scale: Int): Mask {
        val cornerPx = corner * scale
        val shapeW = if (wholeW > 0) wholeW * scale else cornerPx * 2 + 1
        val shapeH = if (wholeH > 0) wholeH * scale else cornerPx * 2 + 1
        val texW = if (borderPx > 0f) shapeW * 2 + GAP else shapeW
        val unbaked = Mask(radiusPx, borderPx, wholeW, wholeH, corner, cornerPx, shapeW, texW, shapeH, null)
        if (failedAt?.let { it.elapsedNow() < RETRY } == true) return unbaked
        val coverage = try {
            rasterize(radiusPx, borderPx, shapeW, shapeH, texW)
        } catch (t: Throwable) {
            if (failedAt == null) Log.main.warn("Glass masks unavailable, drawing square corners", t)
            failedAt = TimeSource.Monotonic.markNow()
            return unbaked
        }
        val image = NativeImage(texW, shapeH, false)
        for (py in 0 until shapeH) for (px in 0 until texW) {
            val a = coverage[py * texW + px].toInt() and 0xFF
            image.setPixelABGR(px, py, (a shl 24) or 0xFFFFFF)
        }
        val id = modId("glass/${nextId++}")
        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "blackaddons-glass" }, image))
        return Mask(radiusPx, borderPx, wholeW, wholeH, corner, cornerPx, shapeW, texW, shapeH, id)
    }

    private fun rasterize(radiusPx: Float, borderPx: Float, shapeW: Int, shapeH: Int, texW: Int): ByteArray {
        val info = ImageInfo.makeA8(texW, shapeH)
        Bitmap().use { bitmap ->
            bitmap.allocPixels(info)
            Canvas(bitmap).use { canvas ->
                canvas.clear(0)
                Paint().use { paint ->
                    paint.setAntiAlias(true)
                    paint.setColor(-1)
                    canvas.drawRRect(RRect.makeXYWH(0f, 0f, shapeW.toFloat(), shapeH.toFloat(), radiusPx), paint)
                    if (borderPx > 0f) {
                        val half = borderPx / 2f
                        paint.setMode(PaintMode.STROKE)
                        paint.setStrokeWidth(borderPx)
                        canvas.drawRRect(
                            RRect.makeXYWH(
                                shapeW + GAP + half, half, shapeW - borderPx, shapeH - borderPx, max(0f, radiusPx - half),
                            ),
                            paint,
                        )
                    }
                }
            }
            return bitmap.readPixels(info, texW.toLong(), 0, 0) ?: error("Glass mask readback failed")
        }
    }

    private fun paint(g: GuiGraphicsExtractor, m: Mask, x: Int, y: Int, w: Int, h: Int, argb: Int, border: Boolean) {
        if (argb ushr 24 == 0) return
        val id = m.id ?: return g.fill(x, y, x + w, y + h, argb)
        val u = if (border) (m.shapeW + GAP).toFloat() else 0f
        if (m.wholeW > 0) {
            blit(g, id, m, x, y, w, h, u, 0f, m.shapeW, m.texH, argb)
            return
        }
        val c = m.corner
        val cp = m.cornerPx
        val far = (cp + 1).toFloat()
        val mid = cp + 0.5f
        val innerW = w - c * 2
        val innerH = h - c * 2
        blit(g, id, m, x, y, c, c, u, 0f, cp, cp, argb)
        blit(g, id, m, x + c, y, innerW, c, u + mid, 0f, 0, cp, argb)
        blit(g, id, m, x + w - c, y, c, c, u + far, 0f, cp, cp, argb)
        blit(g, id, m, x, y + c, c, innerH, u, mid, cp, 0, argb)
        blit(g, id, m, x + c, y + c, innerW, innerH, u + mid, mid, 0, 0, argb)
        blit(g, id, m, x + w - c, y + c, c, innerH, u + far, mid, cp, 0, argb)
        blit(g, id, m, x, y + h - c, c, c, u, far, cp, cp, argb)
        blit(g, id, m, x + c, y + h - c, innerW, c, u + mid, far, 0, cp, argb)
        blit(g, id, m, x + w - c, y + h - c, c, c, u + far, far, cp, cp, argb)
    }

    private fun blit(
        g: GuiGraphicsExtractor, id: Identifier, m: Mask,
        x: Int, y: Int, w: Int, h: Int, u: Float, v: Float, srcW: Int, srcH: Int, argb: Int,
    ) {
        if (w > 0 && h > 0) g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, srcW, srcH, m.texW, m.texH, argb)
    }

    private fun release(mask: Mask) {
        mask.id?.let(Minecraft.getInstance().textureManager::release)
    }

    private fun clear() {
        masks.forEach(::release)
        masks.clear()
        for (id in squares.values) if (id != null) Minecraft.getInstance().textureManager.release(id)
        squares.clear()
    }

    /*
     * Square cells, checkboxes and icon tiles bake whole: one quad each instead of nine. Only squares, since a bar
     * that animates its width would bake a mask per frame.
     */
    private const val WHOLE_MAX = 24
    private const val CHECK = 0
    private const val CORNER = 2
    private const val MAX_MASKS = 48
    private const val GAP = 2
    private val RETRY = 1.seconds
}
