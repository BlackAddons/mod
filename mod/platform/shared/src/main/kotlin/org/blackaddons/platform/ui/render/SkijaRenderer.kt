package org.blackaddons.platform.ui.render

import io.github.humbleui.skija.Data
import io.github.humbleui.skija.FontMgr
import io.github.humbleui.skija.ImageInfo
import io.github.humbleui.skija.Typeface
import net.minecraft.client.Minecraft
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import org.blackaddons.api.ui.render.*
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.util.sanitizeUtf16
import org.blackaddons.blackskija.api.FontStyling
import org.blackaddons.blackskija.api.RichRun
import org.blackaddons.blackskija.api.ShapeBuilder
import org.blackaddons.blackskija.api.Skija
import org.blackaddons.blackskija.api.SkijaFonts
import org.blackaddons.blackskija.api.SkijaLayer
import org.blackaddons.blackskija.api.SkijaShape
import org.blackaddons.blackskija.api.SkijaTextures
import org.blackaddons.blackskija.api.draw.SkijaAnimation
import org.blackaddons.blackskija.api.draw.SkijaImages
import org.blackaddons.blackskija.api.draw.SkijaItems
import org.blackaddons.blackskija.api.draw.SkijaLottie
import org.blackaddons.blackskija.api.draw.SkijaVectors
import org.blackaddons.core.log.Log
import org.joml.Matrix3x2fc
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import io.github.humbleui.skija.Image as SkImage
import io.github.humbleui.skija.skottie.Animation as SkAnimation
import org.blackaddons.blackskija.api.Gradient as SkGradient
import kotlin.time.TimeSource

/**
 * The only [Renderer] talking to BlackSkija, the UI goes through [Render]. Draws append to BlackSkija's per-frame
 * batch, flushed by its compositor. `Render.beginPass` resets the transform mirror per pass. Maps mod font/image paths
 * to Skija families and cached [SkImage]s. Items live in [ItemStacks], network images in [RemoteImages].
 */
object SkijaRenderer : Renderer {

    // Lazy registration, a failed font falls back to the bundled default.
    private val fonts = HashMap<String, String>()
    private val styledFonts = HashMap<Font, String>()

    // Kept so we can ask which characters a face has, and the rest can fall back.
    private val typefaces = HashMap<String, Typeface>()
    private val runs by lazy { TextRuns(family(Fonts.unifont.path).let { typefaces[it] }) }

    // Null cached so a bad source is only tried once.
    private val images = HashMap<String, SkImage?>()
    private val playerSkinIds = HashMap<String, Identifier>()
    private val shapes = HashMap<ShapePath, SkijaShape>()
    private val layers = HashMap<RenderLayer, SkijaLayer>()
    private val lotties = HashMap<String, SkAnimation?>()
    private val animations = HashMap<String, SkijaAnimation>()
    private val played = HashSet<String>()

    // CPU transform mirror for local -> screen mapping (chroma).
    private val transforms = TransformTracker()

    override fun beginPass() = transforms.reset()

    // Mod paths omit the leading slash.
    private fun classpath(path: String): String = if (path.startsWith("/")) path else "/$path"

    private fun family(f: Font): String = styledFonts.getOrPut(f) {
        val base = family(f.path)
        if (!f.bold && !f.italic && f.letterSpacing == 0f) return@getOrPut base
        val styled = "$base#${f.bold}:${f.italic}:${f.letterSpacing}"
        runCatching { SkijaFonts.registerStyled(styled, base, FontStyling(f.bold, f.italic, f.letterSpacing)) }
            .map { styled }
            .getOrElse {
                Log.main.warn("SkijaRenderer: font '{}' could not be styled, drawing it plain", f.path, it)
                base
            }
    }

    private fun family(path: String): String = fonts.getOrPut(path) {
        runCatching {
            val bytes = if (path.startsWith(Render.FILE_SCHEME)) {
                Files.readAllBytes(Path.of(path.removePrefix(Render.FILE_SCHEME)))
            } else {
                SkijaRenderer::class.java.getResourceAsStream(classpath(path))?.use { it.readBytes() }
                    ?: error("not on the classpath")
            }
            val typeface = Data.makeFromBytes(bytes).use { FontMgr.getDefault().makeFromData(it) } ?: error("not a font")
            SkijaFonts.register(path, typeface)
            typefaces[path] = typeface
            path
        }
            .getOrElse {
                Log.main.warn("SkijaRenderer: font '{}' failed to load, using default", path, it)
                SkijaFonts.DEFAULT
            }
    }

    // containsKey, so a cached null doesn't get retried and re-logged every frame.
    private fun resolve(path: String): SkImage? {
        animations[path]?.let { animation ->
            animation.imageAt(elapsedMs())?.let { frame ->
                played += path
                return frame
            }
            animations.remove(path)
            if (!played.remove(path)) {
                images[path] = null
                return null
            }
            images.remove(path)
            return resolve(path)
        }
        images[path]?.let { return it }
        if (images.containsKey(path)) return null

        // Off the render thread, a slow host shouldn't stall a frame.
        if (path.startsWith("http://") || path.startsWith("https://")) return resolveRemote(path)

        if (path.startsWith(Render.FILE_SCHEME)) return resolveFile(path)

        // Local images are cheap, resolve inline.
        val image = runCatching { SkijaImages.resource(classpath(path)) }
            .onFailure { Log.main.warn("SkijaRenderer: image '{}' failed to load", path, it) }
            .getOrNull()
        images[path] = image
        return image
    }

    /** Absent until [RemoteImages] has the bytes. */
    private fun resolveRemote(path: String): SkImage? = when (val fetch = RemoteImages.poll(path)) {
        is RemoteImages.Fetch.Ready -> decode(path, fetch.bytes)
        // Don't refetch a dead URL.
        RemoteImages.Fetch.Failed -> null.also { images[path] = null }
        RemoteImages.Fetch.Pending -> null
    }

    private fun resolveFile(path: String): SkImage? =
        runCatching { decode(path, Files.readAllBytes(Path.of(path.removePrefix(Render.FILE_SCHEME)))) }
            .onFailure {
                Log.main.warn("SkijaRenderer: file image '{}' failed to load", path, it)
                images[path] = null
            }
            .getOrNull()

    private fun decode(path: String, bytes: ByteArray): SkImage? {
        val animation = runCatching { SkijaImages.animated(path, bytes) }.getOrNull()
        if (animation != null && animation.frameCount > 1) {
            animations[path] = animation
            return animation.imageAt(elapsedMs())
        }
        if (animation != null) SkijaImages.delete(path)
        return SkijaImages.fromEncoded(path, bytes).also { images[path] = it }
    }

    private val clock = TimeSource.Monotonic.markNow()

    private fun elapsedMs(): Long = clock.elapsedNow().inWholeMilliseconds

    private fun GradientDir.toSkija(): SkGradient = when (this) {
        GradientDir.LEFT_RIGHT -> SkGradient.LEFT_RIGHT
        GradientDir.TOP_BOTTOM -> SkGradient.TOP_BOTTOM
    }

    override fun push() { Skija.push(); transforms.push() }
    override fun pop() { Skija.pop(); transforms.pop() }
    override fun translate(x: Number, y: Number) { Skija.translate(x, y); transforms.translate(x.toFloat(), y.toFloat()) }
    override fun scale(x: Number, y: Number) { Skija.scale(x, y); transforms.scale(x.toFloat(), y.toFloat()) }
    override fun rotate(angle: Number) { Skija.rotate(angle); transforms.rotate(angle.toFloat()) }
    override fun transform(matrix: Matrix3x2fc) { Skija.transform(matrix); transforms.mul(matrix) }
    override fun globalAlpha(alpha: Number) = Skija.globalAlpha(alpha)
    override fun pushAntiAlias(enabled: Boolean) = Skija.pushAntiAlias(enabled)
    override fun popAntiAlias() = Skija.popAntiAlias()

    override fun toScreen(x: Number, y: Number): FloatArray = transforms.toScreen(x.toFloat(), y.toFloat())

    override fun pushScissor(x: Number, y: Number, w: Number, h: Number) = Skija.pushScissor(x, y, w, h)
    override fun popScissor() = Skija.popScissor()

    override fun line(x1: Number, y1: Number, x2: Number, y2: Number, thickness: Number, color: Color) =
        Skija.line(x1, y1, x2, y2, thickness, color)

    override fun rect(x: Number, y: Number, w: Number, h: Number, color: Color, radius: Number) =
        Skija.rect(x, y, w, h, color, radius)

    override fun rects(rects: List<BatchRect>, color: Color, radius: Number) =
        Skija.rects(rects.map { Skija.BatchRect(it.x, it.y, it.width, it.height) }, color, radius)

    override fun polygon(points: FloatArray, color: Color, corner: Number) = Skija.polygon(points, color, corner)

    override fun createShape(data: PathData): ShapePath = ShapePath().also {
        shapes[it] = Skija.shape { data.visit(ShapeRecorder(this)) }
    }

    override fun deleteShape(shape: ShapePath) {
        shapes.remove(shape)?.let(Skija::deleteShape)
    }

    override fun fillShape(shape: ShapePath, color: Color) {
        shapes[shape]?.let { Skija.fillShape(it, color) }
    }

    override fun strokeShape(shape: ShapePath, thickness: Number, color: Color) {
        shapes[shape]?.let { Skija.strokeShape(it, thickness, color) }
    }

    override fun layer(layer: RenderLayer, x: Number, y: Number, w: Number, h: Number, content: () -> Unit) =
        Skija.layer(layers.getOrPut(layer, ::SkijaLayer), x, y, w, h, content)

    override fun invalidateLayer(layer: RenderLayer) {
        layers[layer]?.invalidate()
    }

    override fun deleteLayer(layer: RenderLayer) {
        layers.remove(layer)?.clear()
    }

    private class ShapeRecorder(private val shape: ShapeBuilder) : PathData.Visitor {
        override fun moveTo(x: Float, y: Float) = shape.moveTo(x, y)
        override fun lineTo(x: Float, y: Float) = shape.lineTo(x, y)
        override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
            shape.cubicTo(c1x, c1y, c2x, c2y, x, y)
        override fun close() = shape.close()
        override fun rect(x: Float, y: Float, w: Float, h: Float) = shape.rect(x, y, w, h)
        override fun polygon(points: FloatArray, corner: Float) = shape.polygon(points, corner)
    }

    override fun hollowRect(x: Number, y: Number, w: Number, h: Number, thickness: Number, color: Color, radius: Number) =
        Skija.hollowRect(x, y, w, h, thickness, color, radius)

    override fun halfRoundedRect(x: Number, y: Number, w: Number, h: Number, color: Color, radius: Number, roundTop: Boolean) =
        Skija.drawHalfRoundedRect(x, y, w, h, color, radius, roundTop)

    override fun gradientRect(x: Number, y: Number, w: Number, h: Number, c1: Color, c2: Color, dir: GradientDir, radius: Number) =
        Skija.gradientRect(x, y, w, h, c1, c2, dir.toSkija(), radius)

    override fun circle(x: Number, y: Number, radius: Number, color: Color) = Skija.circle(x, y, radius, color)

    override fun dropShadow(x: Number, y: Number, w: Number, h: Number, blur: Number, spread: Number, radius: Number, color: Color) =
        Skija.dropShadow(x, y, w, h, blur, spread, radius, color)

    override fun shaderRect(x: Number, y: Number, w: Number, h: Number, sksl: String, uniforms: Map<String, FloatArray>, radius: Number) =
        Skija.shaderRect(x, y, w, h, sksl, uniforms, radius)

    override fun icon(
        data: String, x: Number, y: Number, size: Number, color: Color,
        viewBox: Number, thickness: Number,
    ) = SkijaVectors.icon(data, x, y, size, color, viewBox, thickness)

    override fun text(text: String, x: Number, y: Number, size: Number, color: Color, font: Font) {
        val clean = text.sanitizeUtf16()
        val split = runs.split(clean, typefaces[font.path])
            ?: return Skija.text(clean, font.x(x, size), font.y(y, size), font.px(size), color, family(font))
        drawRuns(split, x.toFloat(), y.toFloat(), size, color, font, shadow = false)
    }

    override fun textShadow(text: String, x: Number, y: Number, size: Number, color: Color, font: Font) {
        val clean = text.sanitizeUtf16()
        val split = runs.split(clean, typefaces[font.path])
            ?: return Skija.textShadow(clean, font.x(x, size), font.y(y, size), font.px(size), color, family(font))
        drawRuns(split, x.toFloat(), y.toFloat(), size, color, font, shadow = true)
    }

    // Every run sits on the font's baseline, so the fallback face lines up with the text around it.
    private fun drawRuns(split: List<TextRun>, x: Float, y: Float, size: Number, color: Color, font: Font, shadow: Boolean) {
        val baseline = font.y(y, size) + Skija.textMetrics(font.px(size), family(font))[0]
        var cx = x
        for (run in split) {
            when (run) {
                is TextRun.Main -> drawPlain(run.text, cx, y, size, color, font, shadow)
                is TextRun.Fallback -> {
                    val fallback = Fonts.unifont
                    val top = baseline - Skija.textMetrics(fallback.px(size), family(fallback))[0] - fallback.shiftY * fallback.px(size)
                    drawPlain(run.text, cx, top, size, color, fallback, shadow)
                }
                is TextRun.Icon -> drawIcon(run.glyph, cx, baseline, font.px(size), color)
            }
            cx += runWidth(run, size, font)
        }
    }

    private fun drawPlain(text: String, x: Float, y: Float, size: Number, color: Color, font: Font, shadow: Boolean) {
        if (shadow) Skija.textShadow(text, font.x(x, size), font.y(y, size), font.px(size), color, family(font))
        else Skija.text(text, font.x(x, size), font.y(y, size), font.px(size), color, family(font))
    }

    // A game pixel of Hypixel's 9 px tall font, at this text size.
    private fun iconUnit(px: Float) = px / GAME_LINE_HEIGHT

    private fun drawIcon(glyph: HypixelGlyphs.Glyph, x: Float, baseline: Float, px: Float, color: Color) {
        val unit = iconUnit(px) * glyph.height / glyph.srcH
        val image = Image(glyph.sheet)
        resolve(image.path)?.let {
            Skija.image(
                it, glyph.srcX, glyph.srcY, glyph.srcW, glyph.srcH,
                x, baseline - glyph.ascent * iconUnit(px), glyph.srcW * unit, glyph.srcH * unit, 0f, color,
            )
        }
    }

    private fun runWidth(run: TextRun, size: Number, font: Font): Float = when (run) {
        is TextRun.Main -> Skija.textWidth(run.text, font.px(size), family(font))
        is TextRun.Fallback -> Skija.textWidth(run.text, Fonts.unifont.px(size), family(Fonts.unifont))
        is TextRun.Icon -> (run.glyph.srcW * run.glyph.height / run.glyph.srcH.toFloat() + 1f) * iconUnit(font.px(size))
    }

    override fun textGradient(text: String, x: Number, y: Number, size: Number,
                              c1: Color, c2: Color, font: Font, dir: GradientDir) =
        Skija.textGradient(text.sanitizeUtf16(), font.x(x, size), font.y(y, size), font.px(size), c1, c2, dir.toSkija(), family(font))

    override fun textWidth(text: String, size: Number, font: Font): Float {
        val clean = text.sanitizeUtf16()
        val split = runs.split(clean, typefaces[font.path]) ?: return Skija.textWidth(clean, font.px(size), family(font))
        return split.sumOf { runWidth(it, size, font).toDouble() }.toFloat()
    }

    override fun textMetrics(size: Number, font: Font): FloatArray =
        Skija.textMetrics(font.px(size), family(font))

    override fun wrappedText(text: String, x: Number, y: Number, w: Number, size: Number,
                             color: Color, font: Font, lineHeight: Number) {
        Skija.wrappedText(text.sanitizeUtf16(), font.x(x, size), font.y(y, size), w, font.px(size), color, family(font), lineHeight)
    }

    // Skija returns [width, height], the contract is [minX, minY, maxX, maxY] from the origin.
    override fun wrappedTextBounds(text: String, w: Number, size: Number, font: Font, lineHeight: Number): FloatArray {
        val wh = Skija.wrappedTextBounds(text.sanitizeUtf16(), w, font.px(size), family(font), lineHeight)
        return floatArrayOf(0f, 0f, wh[0], wh[1])
    }

    override fun richText(spans: List<RichSpan>, x: Number, y: Number, w: Number, size: Number,
                          color: Color, font: Font, lineHeight: Number) {
        Skija.richText(runs(spans), font.x(x, size), font.y(y, size), w, font.px(size), color, family(font), lineHeight)
    }

    override fun richTextBounds(spans: List<RichSpan>, w: Number, size: Number, font: Font, lineHeight: Number): FloatArray {
        val wh = Skija.richTextBounds(runs(spans), w, font.px(size), family(font), lineHeight)
        return floatArrayOf(0f, 0f, wh[0], wh[1])
    }

    private fun Font.px(size: Number) = size.toFloat() * scale

    private fun Font.x(x: Number, size: Number) = x.toFloat() + shiftX * px(size)

    private fun Font.y(y: Number, size: Number) = y.toFloat() + shiftY * px(size)

    // An undecoded picture keeps its box so the line doesn't reflow.
    private fun runs(spans: List<RichSpan>): List<RichRun> = spans.map { span ->
        when (span) {
            is RichSpan.Text -> RichRun.Text(span.text.sanitizeUtf16(), span.color, span.scale)
            is RichSpan.Picture -> resolve(span.path)?.let { image ->
                RichRun.Picture(
                    image, span.srcX, span.srcY,
                    if (span.srcW > 0f) span.srcW else image.width.toFloat(),
                    if (span.srcH > 0f) span.srcH else image.height.toFloat(),
                    span.scale,
                )
            } ?: RichRun.Picture(blank, scale = span.scale)
        }
    }

    // Transparent pixel for pictures that are still loading.
    private val blank: SkImage by lazy {
        val surface = io.github.humbleui.skija.Surface.makeRaster(ImageInfo.makeN32Premul(1, 1))
        surface.makeImageSnapshot().also { surface.close() }
    }

    override fun createImage(path: String): Image {
        resolve(path)
        return Image(path)
    }

    override fun deleteImage(image: Image) {
        // Free under whichever key it was cached.
        images.remove(image.path)?.let {
            runCatching {
                SkijaImages.delete(classpath(image.path))
                SkijaImages.delete(image.path)
            }
        }
        animations.remove(image.path)?.let { runCatching { SkijaImages.delete(image.path) } }
        played -= image.path
    }

    override fun image(image: Image, x: Number, y: Number, w: Number, h: Number, radius: Number) {
        resolve(image.path)?.let { Skija.image(it, x, y, w, h, radius) }
    }

    override fun image(path: String, x: Number, y: Number, w: Number, h: Number, radius: Number) {
        resolve(path)?.let { Skija.image(it, x, y, w, h, radius) }
    }

    override fun imageSize(path: String): FloatArray? =
        resolve(path)?.let { floatArrayOf(it.width.toFloat(), it.height.toFloat()) }

    override fun image(image: Image, x: Number, y: Number, w: Number, h: Number,
                       srcX: Number, srcY: Number, srcW: Number, srcH: Number,
                       texW: Number, texH: Number, tint: Color?, radius: Number) {
        // Skija samples in image pixels, texW/texH are unused.
        resolve(image.path)?.let { Skija.image(it, srcX, srcY, srcW, srcH, x, y, w, h, radius, tint) }
    }

    override fun lottie(path: String, x: Number, y: Number, w: Number, h: Number) {
        val animation = lotties.getOrPut(path) {
            runCatching { SkijaLottie.animation(path, Files.readString(Path.of(path.removePrefix(Render.FILE_SCHEME)))) }
                .onFailure { Log.main.warn("SkijaRenderer: lottie '{}' failed to load", path, it) }
                .getOrNull()
        } ?: return
        Skija.lottie(animation, elapsedMs(), x, y, w, h)
    }

    override fun item(id: String, x: Number, y: Number, w: Number, h: Number, radius: Number, tint: Color?) {
        val stack = ItemStacks.forId(id) ?: return itemPlaceholder(x, y, w, h, radius)
        SkijaItems.draw(stack, x, y, w, h, radius, tint)
    }

    override fun itemSpec(json: String, x: Number, y: Number, w: Number, h: Number, radius: Number) {
        val stack = ItemStacks.forSpec(json) ?: return itemPlaceholder(x, y, w, h, radius)
        SkijaItems.draw(stack, x, y, w, h, radius, null)
    }

    override fun playerHead(uuid: String, name: String, textures: String?,
                            x: Number, y: Number, w: Number, h: Number, radius: Number) {
        val stack = ItemStacks.forPlayerHead(uuid, name, textures)
            ?: return itemPlaceholder(x, y, w, h, radius)
        SkijaItems.draw(stack, x, y, w, h, radius, null)
    }

    override fun playerFace(name: String, x: Number, y: Number, w: Number, h: Number, showHat: Boolean, radius: Number): Boolean {
        val skinId = playerSkinIds[name] ?: run {
            val info = Minecraft.getInstance().connection?.onlinePlayers?.firstOrNull {
                it.profile.name.equals(name, ignoreCase = true)
            } ?: return false
            /* A default skin's id() isn't its texture path, and it only stands in until the real skin downloads. */
            val body = info.skin.body()
            body.texturePath().also { if (body is ClientAsset.DownloadedTexture) playerSkinIds[name] = it }
        }
        val view = runCatching {
            Minecraft.getInstance().textureManager.getTexture(skinId).textureView
        }.getOrNull() ?: return false
        val image = SkijaTextures.wrap(view, premultiplied = false) ?: return false
        val scaleX = image.width / 64f
        val scaleY = image.height / 64f
        Skija.image(image, 8f * scaleX, 8f * scaleY, 8f * scaleX, 8f * scaleY, x, y, w, h, radius, null)
        if (showHat) {
            Skija.image(image, 40f * scaleX, 8f * scaleY, 8f * scaleX, 8f * scaleY, x, y, w, h, radius, null)
        }
        return true
    }

    /**
     * Faint tile for items that can't be built yet (before a world loads), so icons don't look broken. A flat constant,
     * the backend has no palette opinion.
     */
    private fun itemPlaceholder(x: Number, y: Number, w: Number, h: Number, radius: Number) {
        val corner = if (radius.toFloat() > 0f) radius else PLACEHOLDER_RADIUS
        rect(x, y, w, h, PLACEHOLDER_FILL, corner)
    }

    private val PLACEHOLDER_FILL = Color(255, 255, 255, 26)
    private const val PLACEHOLDER_RADIUS = 2f

    // Minecraft's font is 9 game pixels line to line, a TTF of the same size is about as tall.
    private const val GAME_LINE_HEIGHT = 9f
}
