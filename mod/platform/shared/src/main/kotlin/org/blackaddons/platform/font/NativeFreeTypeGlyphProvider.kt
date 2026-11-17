package org.blackaddons.platform.font

import com.mojang.blaze3d.font.GlyphBitmap
import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.font.UnbakedGlyph
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntSet
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph
import net.minecraft.client.gui.font.glyphs.EmptyGlyph
import net.minecraft.client.gui.font.providers.FreeTypeUtil
import net.minecraft.util.ARGB
import org.blackaddons.api.util.near
import org.blackaddons.core.font.ArgbGlyphMask
import org.blackaddons.core.font.GlyphMask
import org.blackaddons.core.font.composeBlackOutline
import org.blackaddons.core.font.composeBlackShadow
import org.blackaddons.core.font.composeGlyphMasks
import org.blackaddons.core.font.padGlyphMask
import org.blackaddons.core.font.whiteArgbMask
import org.blackaddons.platform.compat.GpuTexture
import org.blackaddons.platform.compat.uploadFontMask
import org.blackaddons.platform.mixin.font.BakedSheetGlyphAccessor
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Bitmap
import org.lwjgl.util.freetype.FT_BitmapGlyph
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_Glyph
import org.lwjgl.util.freetype.FT_GlyphSlot
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal class NativeFreeTypeGlyphProvider(
    fontMemory: SharedFontMemory,
    face: FT_Face,
    rasterSize: Float,
    internal val oversample: Float,
    shiftX: Float,
    shiftY: Float,
    internal val outlineWidth: Float,
    private val outlineColorArgb: Int,
    shadowColorArgb: Int,
    shadowOffsetX: Float,
    shadowOffsetY: Float,
) : GlyphProvider {
    private var fontMemory: SharedFontMemory? = fontMemory
    private var face: FT_Face? = face
    private var stroker = 0L
    private var glowStroker = 0L
    private val glyphIndexes = Int2IntOpenHashMap().apply { defaultReturnValue(0) }
    private val glyphs = Int2ObjectOpenHashMap<UnbakedGlyph>()
    private val bakedShadow = blackShadow(shadowColorArgb, shadowOffsetX, shadowOffsetY, oversample)

    init {
        synchronized(face) {
            FreeType.FT_Set_Pixel_Sizes(face, (rasterSize * oversample).roundToInt(), (rasterSize * oversample).roundToInt())
            MemoryStack.stackPush().use { stack ->
                val delta = FreeTypeUtil.setVector(
                    FT_Vector.malloc(stack), shiftX * oversample, -shiftY * oversample,
                )
                FreeType.FT_Set_Transform(face, null, delta)
                val index = stack.mallocInt(1)
                var codePoint = FreeType.FT_Get_First_Char(face, index).toInt()
                var glyphIndex = index[0]
                while (glyphIndex != 0) {
                    glyphIndexes.put(codePoint, glyphIndex)
                    codePoint = FreeType.FT_Get_Next_Char(face, codePoint.toLong(), index).toInt()
                    glyphIndex = index[0]
                }
                if (outlineWidth > 0f) stroker = createStroker(stack, outlineWidth)
                glowStroker = createStroker(stack, GLOW_OUTLINE_WIDTH)
            }
        }
        fontMemory.retain()
    }

    private fun createStroker(stack: MemoryStack, width: Float): Long {
        val strokerPointer = stack.mallocPointer(1)
        FreeTypeUtil.assertError(
            FreeType.FT_Stroker_New(FreeTypeUtil.getLibrary(), strokerPointer),
            "Creating global font outline stroker",
        )
        FreeType.FT_Stroker_Set(
            strokerPointer[0], (width * oversample * 64f).roundToLong().coerceAtLeast(1L),
            FreeType.FT_STROKER_LINECAP_ROUND,
            FreeType.FT_STROKER_LINEJOIN_ROUND,
            0L,
        )
        return strokerPointer[0]
    }

    override fun getGlyph(codePoint: Int): UnbakedGlyph? {
        glyphs.get(codePoint)?.let { return it }
        val index = glyphIndexes.get(codePoint)
        if (index == 0) return null
        val loaded = synchronized(validateFace()) {
            val openFace = validateFace()
            FreeTypeUtil.assertError(
                FreeType.FT_Load_Glyph(openFace, index, METRICS_LOAD_FLAGS),
                "Loading global font glyph metrics",
            )
            val slot = requireNotNull(openFace.glyph()) { "Global font glyph was not initialized" }
            val advance = FreeTypeUtil.x(slot.advance()) / oversample
            val bitmap = slot.bitmap()
            if (bitmap.width() <= 0 || bitmap.rows() <= 0) EmptyGlyph(advance)
            else NativeFreeTypeUnbakedGlyph(this, index, GlyphInfo.simple(advance))
        }
        glyphs.put(codePoint, loaded)
        return loaded
    }

    override fun getSupportedGlyphs(): IntSet = glyphIndexes.keys

    internal fun bakeStyle(
        index: Int,
        bold: Boolean,
        info: GlyphInfo,
        stitcher: UnbakedGlyph.Stitcher,
    ): NativeBakedStyle = prepareStyle(index, bold).stitch(info, stitcher)

    internal fun prepareStyle(index: Int, bold: Boolean): PreparedStyle {
        val style = prepareBaseStyle(index, bold)
        return PreparedStyle { info, stitcher ->
            style.stitch(info, stitcher).copy(glow = LazyBakedValue { prepareGlowStyle(index, bold).stitch(info, stitcher) })
        }
    }

    private fun prepareGlowStyle(index: Int, bold: Boolean): PreparedStyle {
        val fill = rasterize(index, bold, stroked = false) ?: return MISSING_STYLE
        val border = rasterize(index, bold, stroked = true, glowStroker) ?: return MISSING_STYLE
        return outlinedStyle(fill, border)
    }

    private fun prepareBaseStyle(index: Int, bold: Boolean): PreparedStyle {
        val fill = rasterize(index, bold, stroked = false) ?: return MISSING_STYLE
        val border = if (outlineWidth > 0f) rasterize(index, bold, stroked = true) else null
        if (border != null && outlineColorArgb and 0x00FFFFFF != 0) return outlinedStyle(fill, border)
        val fillShadow = if (bakedShadow == null) null else shadowOf(whiteArgbMask(fill.mask()))
        if (border == null) {
            return PreparedStyle { info, stitcher ->
                val shadowedPlain = fillShadow?.let { stitcher.stitch(info, it) }
                NativeBakedStyle(
                    stitcher.stitch(info, fill), null, shadowedFill = shadowedPlain, shadowedPlainFill = shadowedPlain,
                )
            }
        }
        val integratedMask = composeBlackOutline(fill.mask(), border.mask(), outlineColorArgb ushr 24)
        val integratedShadow = shadowOf(integratedMask)
        return PreparedStyle { info, stitcher ->
            NativeBakedStyle(
                stitcher.stitch(info, coloredGlyph(integratedMask, oversample)), null, stitcher.stitch(info, fill),
                integratedOutline = true,
                shadowedFill = integratedShadow?.let { stitcher.stitch(info, it) },
                shadowedPlainFill = fillShadow?.let { stitcher.stitch(info, it) },
            )
        }
    }

    private fun outlinedStyle(fill: RasterizedGlyph, border: RasterizedGlyph): PreparedStyle {
        val outline = combine(fill, border)
        val sheet = sideBySide(outline, fill)
        return PreparedStyle { info, stitcher ->
            val stitched = stitcher.stitch(info, sheet)
            require(stitched is BakedSheetGlyph) { "Global font outline was not stitched into the font atlas" }
            NativeBakedStyle(stitched.region(info, sheet, outline.width, fill), stitched.region(info, sheet, 0, outline))
        }
    }

    private fun rasterize(index: Int, bold: Boolean, stroked: Boolean, borderStroker: Long = stroker): RasterizedGlyph? =
        synchronized(validateFace()) {
            val openFace = validateFace()
            FreeTypeUtil.assertError(
                FreeType.FT_Load_Glyph(openFace, index, FreeType.FT_LOAD_NO_BITMAP),
                "Loading global font glyph outline",
            )
            val slot = requireNotNull(openFace.glyph()) { "Global font glyph was not initialized" }
            if (bold) embolden(slot)
            if (stroked) rasterizeBorder(slot, borderStroker) else {
                FreeTypeUtil.assertError(
                    FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_NORMAL),
                    "Rasterizing global font glyph",
                )
                copyBitmap(slot.bitmap(), slot.bitmap_left(), slot.bitmap_top())
            }
        }

    private fun embolden(slot: FT_GlyphSlot) {
        val strength = (BOLD_STRENGTH * oversample * 64f).roundToLong()
        if (strength <= 0L) return
        FreeTypeUtil.assertError(
            FreeType.FT_Outline_EmboldenXY(slot.outline(), strength, strength),
            "Emboldening global font glyph",
        )
        FreeType.FT_Outline_Translate(slot.outline(), -strength / 2L, -strength / 2L)
    }

    private fun rasterizeBorder(slot: FT_GlyphSlot, stroker: Long): RasterizedGlyph? = MemoryStack.stackPush().use { stack ->
        val glyphPointer = stack.mallocPointer(1)
        FreeTypeUtil.assertError(
            FreeType.FT_Get_Glyph(slot, glyphPointer),
            "Copying global font glyph outline",
        )
        try {
            check(stroker != 0L) { "Global font outline stroker was not initialized" }
            FreeTypeUtil.assertError(
                FreeType.FT_Glyph_StrokeBorder(glyphPointer, stroker, false, true),
                "Stroking global font glyph outline",
            )
            FreeTypeUtil.assertError(
                FreeType.FT_Glyph_To_Bitmap(glyphPointer, FreeType.FT_RENDER_MODE_NORMAL, null, true),
                "Rasterizing global font glyph outline",
            )
            val bitmapGlyph = FT_BitmapGlyph.create(glyphPointer[0])
            copyBitmap(bitmapGlyph.bitmap(), bitmapGlyph.left(), bitmapGlyph.top())
        } finally {
            glyphPointer[0].takeIf { it != 0L }?.let { FreeType.FT_Done_Glyph(FT_Glyph.create(it)) }
        }
    }

    private fun copyBitmap(bitmap: FT_Bitmap, left: Int, top: Int): RasterizedGlyph? {
        val width = bitmap.width()
        val height = bitmap.rows()
        if (width <= 0 || height <= 0) return null
        require(bitmap.pixel_mode().toInt() and 0xFF == FreeType.FT_PIXEL_MODE_GRAY) {
            "Global font glyph was not rendered as grayscale"
        }
        val pitch = bitmap.pitch()
        val stride = abs(pitch)
        require(stride >= width) { "Global font glyph has an invalid bitmap pitch" }
        val source = requireNotNull(bitmap.buffer(stride * height)) { "Global font glyph has no bitmap" }
        val pixels = ByteArray(width * height)
        for (y in 0 until height) {
            val sourceY = if (pitch >= 0) y else height - 1 - y
            val sourceOffset = sourceY * stride
            val targetOffset = y * width
            for (x in 0 until width) pixels[targetOffset + x] = source[sourceOffset + x]
        }
        val padded = padGlyphMask(GlyphMask(width, height, left, top, pixels), GLYPH_PADDING)
        return RasterizedGlyph(
            padded.width, padded.height, padded.left, padded.top, oversample, padded.pixels,
        )
    }

    override fun close() {
        val closingFace = face
        if (closingFace != null) {
            synchronized(closingFace) {
                synchronized(FreeTypeUtil.LIBRARY_LOCK) {
                    if (stroker != 0L) FreeType.FT_Stroker_Done(stroker)
                    stroker = 0L
                    if (glowStroker != 0L) FreeType.FT_Stroker_Done(glowStroker)
                    glowStroker = 0L
                    FreeTypeUtil.checkError(FreeType.FT_Done_Face(closingFace), "Deleting global font face")
                }
                face = null
            }
        }
        fontMemory?.release()
        fontMemory = null
    }

    private fun validateFace(): FT_Face = face ?: error("Global font provider is already closed")

    internal companion object {
        const val BOLD_STRENGTH = 0.5f
        private const val GLOW_OUTLINE_WIDTH = 1f
        private val MISSING_STYLE = PreparedStyle { _, stitcher -> NativeBakedStyle(stitcher.missing, null) }
        private const val GLYPH_PADDING = 1
        private const val METRICS_LOAD_FLAGS = FreeType.FT_LOAD_NO_BITMAP or FreeType.FT_LOAD_BITMAP_METRICS_ONLY
        private const val UV_INSET = 0.01f

        private fun combine(fill: RasterizedGlyph, border: RasterizedGlyph): RasterizedGlyph {
            val mask = composeGlyphMasks(fill.mask(), border.mask()).mask
            return RasterizedGlyph(mask.width, mask.height, mask.left, mask.top, fill.rasterScale, mask.pixels)
        }

        private fun sideBySide(left: RasterizedGlyph, right: RasterizedGlyph): RasterizedGlyph {
            val width = left.width + right.width
            val height = maxOf(left.height, right.height)
            val pixels = ByteArray(width * height)
            for (y in 0 until left.height) System.arraycopy(left.pixels, y * left.width, pixels, y * width, left.width)
            for (y in 0 until right.height) {
                System.arraycopy(right.pixels, y * right.width, pixels, y * width + left.width, right.width)
            }
            return RasterizedGlyph(width, height, left.left, left.top, left.rasterScale, pixels)
        }

        private fun BakedSheetGlyph.region(info: GlyphInfo, sheet: RasterizedGlyph, x: Int, part: RasterizedGlyph): BakedSheetGlyph {
            val access = this as BakedSheetGlyphAccessor
            val u0 = access.`blackaddons$getU0`()
            val v0 = access.`blackaddons$getV0`()
            val uScale = (access.`blackaddons$getU1`() - u0) / (sheet.width - UV_INSET * 2f)
            val vScale = (access.`blackaddons$getV1`() - v0) / (sheet.height - UV_INSET * 2f)
            val baseU = u0 - UV_INSET * uScale
            val baseV = v0 - UV_INSET * vScale
            val left = part.left / part.rasterScale
            val top = 7f - part.top / part.rasterScale
            return BakedSheetGlyph(
                info,
                access.`blackaddons$getRenderTypes`(),
                access.`blackaddons$getTextureView`(),
                baseU + (x + UV_INSET) * uScale, baseU + (x + part.width - UV_INSET) * uScale,
                baseV + UV_INSET * vScale, baseV + (part.height - UV_INSET) * vScale,
                left, left + part.width / part.rasterScale, top, top + part.height / part.rasterScale,
            )
        }

        private fun RasterizedGlyph.mask(): GlyphMask = GlyphMask(width, height, left, top, pixels)

        private fun coloredGlyph(mask: ArgbGlyphMask, rasterScale: Float) =
            ColoredGlyphBitmap(mask.width, mask.height, mask.left, mask.top, rasterScale, mask.pixels)

        private fun blackShadow(
            colorArgb: Int,
            offsetX: Float,
            offsetY: Float,
            oversample: Float,
        ): BakedBlackShadow? {
            val alpha = colorArgb ushr 24
            if (alpha == 0 || colorArgb and 0x00FFFFFF != 0) return null
            val scaledX = offsetX * oversample
            val scaledY = offsetY * oversample
            val pixelX = scaledX.roundToInt()
            val pixelY = scaledY.roundToInt()
            if (!scaledX.near(pixelX.toFloat()) || !scaledY.near(pixelY.toFloat())) return null
            return BakedBlackShadow(alpha, pixelX, pixelY)
        }
    }

    private fun shadowOf(source: ArgbGlyphMask): GlyphBitmap? {
        val shadow = bakedShadow ?: return null
        return coloredGlyph(composeBlackShadow(source, shadow.alpha, shadow.offsetX, shadow.offsetY), oversample)
    }
}

internal fun interface PreparedStyle {
    fun stitch(info: GlyphInfo, stitcher: UnbakedGlyph.Stitcher): NativeBakedStyle
}

interface SmoothGlyphBitmap : GlyphBitmap

internal class SharedFontMemory(val buffer: ByteBuffer) {
    private val references = AtomicInteger(1)

    fun retain() {
        references.incrementAndGet()
    }

    fun release() {
        if (references.decrementAndGet() == 0) MemoryUtil.memFree(buffer)
    }
}

private data class BakedBlackShadow(val alpha: Int, val offsetX: Int, val offsetY: Int)

internal class NativeFreeTypeUnbakedGlyph(
    private val provider: NativeFreeTypeGlyphProvider,
    val index: Int,
    private val sourceInfo: GlyphInfo,
) : UnbakedGlyph {
    override fun info(): GlyphInfo = sourceInfo

    override fun bake(stitcher: UnbakedGlyph.Stitcher): BakedGlyph =
        provider.bakeStyle(index, bold = false, sourceInfo, stitcher).fill

    fun bakeStyle(bold: Boolean, info: GlyphInfo, stitcher: UnbakedGlyph.Stitcher): NativeBakedStyle =
        provider.bakeStyle(index, bold, info, stitcher)
}

internal data class NativeBakedStyle(
    val fill: BakedGlyph,
    val outline: BakedGlyph?,
    val plainFill: BakedGlyph = fill,
    val integratedOutline: Boolean = false,
    val shadowedFill: BakedGlyph? = null,
    val shadowedPlainFill: BakedGlyph? = shadowedFill,
    val glow: LazyBakedValue<NativeBakedStyle>? = null,
)

private class RasterizedGlyph(
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int,
    val rasterScale: Float,
    val pixels: ByteArray,
) : SmoothGlyphBitmap {
    override fun getPixelWidth(): Int = width
    override fun getPixelHeight(): Int = height
    override fun isColored(): Boolean = false
    override fun getOversample(): Float = rasterScale
    override fun getBearingLeft(): Float = left / rasterScale
    override fun getBearingTop(): Float = top / rasterScale

    override fun upload(x: Int, y: Int, texture: GpuTexture) {
        NativeImage(NativeImage.Format.LUMINANCE, width, height, false).use { image ->
            MemoryUtil.memByteBuffer(image.pointer, pixels.size).put(pixels)
            uploadFontMask(RenderSystem.getDevice().createCommandEncoder(), texture, image, x, y)
        }
    }
}

private class ColoredGlyphBitmap(
    private val width: Int,
    private val height: Int,
    private val left: Int,
    private val top: Int,
    private val rasterScale: Float,
    private val pixels: IntArray,
) : SmoothGlyphBitmap {
    override fun getPixelWidth(): Int = width
    override fun getPixelHeight(): Int = height
    override fun isColored(): Boolean = true
    override fun getOversample(): Float = rasterScale
    override fun getBearingLeft(): Float = left / rasterScale
    override fun getBearingTop(): Float = top / rasterScale

    override fun upload(x: Int, y: Int, texture: GpuTexture) {
        NativeImage(NativeImage.Format.RGBA, width, height, false).use { image ->
            val target = MemoryUtil.memIntBuffer(image.pointer, pixels.size)
            for (pixel in pixels) target.put(ARGB.toABGR(pixel))
            uploadFontMask(RenderSystem.getDevice().createCommandEncoder(), texture, image, x, y)
        }
    }
}
