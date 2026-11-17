package org.blackaddons.platform.font

import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.font.UnbakedGlyph
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.ints.IntSet
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.font.glyphs.EmptyGlyph
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.network.chat.Style
import org.blackaddons.core.font.GlobalFontRequest
import org.blackaddons.platform.compat.GpuTextureView
import org.blackaddons.platform.compat.RenderPipeline
import org.joml.Matrix4fc
import java.util.IdentityHashMap

internal class StyledGlyphProvider(
    private val screenDelegates: List<GlyphProvider>,
    private val worldDelegates: List<GlyphProvider>,
    private val request: GlobalFontRequest,
    internal val ownsDelegate: Boolean,
) : GlyphProvider {
    internal val screenDelegate: GlyphProvider get() = screenDelegates.first()

    override fun getGlyph(codePoint: Int): UnbakedGlyph? {
        val screen = screenDelegate.getGlyph(codePoint) ?: return null
        return StyledUnbakedGlyph(codePoint, screen, screenDelegates, worldDelegates, request)
    }

    override fun getSupportedGlyphs(): IntSet = screenDelegate.supportedGlyphs

    override fun close() {
        if (!ownsDelegate) return
        (screenDelegates + worldDelegates).distinct().forEach(GlyphProvider::close)
    }
}

internal class StyledUnbakedGlyph(
    private val codePoint: Int,
    private val screen: UnbakedGlyph,
    private val screenDelegates: List<GlyphProvider>,
    private val worldDelegates: List<GlyphProvider>,
    private val request: GlobalFontRequest,
) : UnbakedGlyph {
    private val native = screen is NativeFreeTypeUnbakedGlyph
    private val styledInfo = StyledGlyphInfo(screen.info(), native, request)

    override fun info(): GlyphInfo = screen.info()

    override fun bake(stitcher: UnbakedGlyph.Stitcher): BakedGlyph {
        if (screen is EmptyGlyph) return StyledEmptyBakedGlyph(styledInfo)
        val baked = IdentityHashMap<GlyphProvider, LazyBakedValue<BakedStyles>>()
        fun variant(delegate: GlyphProvider) = baked.getOrPut(delegate) {
            LazyBakedValue {
                val glyph = if (delegate === screenDelegates.first()) screen else delegate.getGlyph(codePoint) ?: screen
                bakeStyles(glyph, stitcher)
            }
        }
        val screens = screenDelegates.map(::variant)
        val worlds = worldDelegates.mapIndexed { bucket, delegate ->
            if (bucket == 0 || screen !is NativeFreeTypeUnbakedGlyph || delegate !is NativeFreeTypeGlyphProvider || delegate in screenDelegates) {
                SyncWorldStyles(variant(delegate))
            } else {
                BackgroundWorldStyles({ bold -> delegate.prepareStyle(screen.index, bold) }, styledInfo, stitcher)
            }
        }
        return ContextualBakedGlyph(screens, worlds, styledInfo, request)
    }

    private fun bakeStyles(glyph: UnbakedGlyph, stitcher: UnbakedGlyph.Stitcher): BakedStyles {
        if (glyph !is NativeFreeTypeUnbakedGlyph) {
            return BakedStyles(NativeBakedStyle(glyph.bake(stitcher), null), null, native = false)
        }
        val regular = glyph.bakeStyle(request.forceBold, styledInfo, stitcher)
        val bold = if (request.forceBold) null else LazyBakedValue {
            glyph.bakeStyle(bold = true, styledInfo, stitcher)
        }
        return BakedStyles(regular, bold, native = true)
    }
}

internal class StyledGlyphInfo(
    private val delegate: GlyphInfo,
    private val native: Boolean,
    request: GlobalFontRequest,
) : GlyphInfo {
    private val forceBold = request.forceBold
    private val regularAdvance = advance(request, bold = false)
    private val boldAdvance = advance(request, bold = true)

    override fun getAdvance(): Float = if (forceBold) boldAdvance else regularAdvance

    override fun getAdvance(bold: Boolean): Float = if (bold || forceBold) boldAdvance else regularAdvance

    override fun getBoldOffset(): Float = if (native) 0f else delegate.boldOffset

    override fun getShadowOffset(): Float = delegate.shadowOffset

    private fun advance(request: GlobalFontRequest, bold: Boolean): Float = if (native) {
        delegate.advance + request.letterSpacing + outlineSpacing(request) +
            if (bold) NativeFreeTypeGlyphProvider.BOLD_STRENGTH else 0f
    } else {
        delegate.getAdvance(bold) + request.letterSpacing
    }

    private fun outlineSpacing(request: GlobalFontRequest): Float = request.outline
        .takeIf { it.enabled && it.width > 0f && it.colorArgb ushr 24 != 0 }
        ?.let { it.width * 2f }
        ?: 0f
}

internal class StyledEmptyBakedGlyph(private val styledInfo: GlyphInfo) : BakedGlyph {
    override fun info(): GlyphInfo = styledInfo

    override fun createGlyph(
        x: Float,
        y: Float,
        color: Int,
        shadowColor: Int,
        style: Style,
        boldOffset: Float,
        shadowOffset: Float,
    ): TextRenderable.Styled? = null
}

internal class ContextualBakedGlyph(
    private val screens: List<LazyBakedValue<BakedStyles>>,
    private val worlds: List<WorldStyles>,
    private val styledInfo: GlyphInfo,
    private val request: GlobalFontRequest,
) : BakedGlyph {
    override fun info(): GlyphInfo = styledInfo

    override fun createGlyph(
        x: Float,
        y: Float,
        color: Int,
        shadowColor: Int,
        style: Style,
        boldOffset: Float,
        shadowOffset: Float,
    ): TextRenderable.Styled? {
        val wantsBold = request.forceBold || style.isBold
        val effectsSuppressed = GlobalFontEffects.suppressed
        when (GlobalFontRenderContext.current) {
            GlobalFontRenderContext.SCREEN -> return createScreenGlyph(
                screens[0], request, wantsBold, x, y, color, shadowColor,
                style, boldOffset, shadowOffset, effectsSuppressed,
            )
            GlobalFontRenderContext.SCREEN_2X -> return createScreenGlyph(
                screens[1], request, wantsBold, x, y, color, shadowColor,
                style, boldOffset, shadowOffset, effectsSuppressed,
            )
            GlobalFontRenderContext.SCREEN_4X -> return createScreenGlyph(
                screens[2], request, wantsBold, x, y, color, shadowColor,
                style, boldOffset, shadowOffset, effectsSuppressed,
            )
            GlobalFontRenderContext.WORLD -> {
                val variants = world(wantsBold)
                val glowOutline = GlobalFontEffects.glowOutline
                if (glowOutline != 0) {
                    variants.select(wantsBold).glow?.get()?.takeIf { it.outline != null }?.let { glow ->
                        if (effectsSuppressed) return null
                        return glowGlyph(glow, x, y, color, glowOutline, style, shadowOffset)
                            ?.let { OrderedDepthGlyph(it, GlobalFontRenderContext.nextWorldGlyphDepth()) }
                    }
                }
                return createStyledGlyph(
                    variants, request, wantsBold, true, x, y, color, shadowColor,
                    style, boldOffset, shadowOffset, effectsSuppressed || glowOutline != 0,
                )?.let { OrderedDepthGlyph(it, GlobalFontRenderContext.nextWorldGlyphDepth()) } ?: createScreenGlyph(
                    screens[0], request, wantsBold, x, y, color, shadowColor,
                    style, boldOffset, shadowOffset, effectsSuppressed,
                )
            }
        }
        return ContextualGlyph(
            screens[0], worlds.last(), request, wantsBold, x, y, color, shadowColor,
            style, boldOffset, shadowOffset, effectsSuppressed,
        )
    }

    private fun glowGlyph(
        glow: NativeBakedStyle,
        x: Float,
        y: Float,
        color: Int,
        glowOutline: Int,
        style: Style,
        shadowOffset: Float,
    ): TextRenderable.Styled? {
        val renderStyle = RenderStyles.of(style, request.forceItalic, request.forceBold, native = true)
        val main = glow.fill.createGlyph(x, y, color, 0, renderStyle, 0f, shadowOffset) ?: return null
        val outline = glow.outline?.createGlyph(
            x, y, withSourceAlpha(glowOutline, color), 0, renderStyle, 0f, shadowOffset,
        )
        return LayeredGlyph(null, outline, main, world = true)
    }

    private fun world(bold: Boolean): BakedStyles {
        val wanted = GlobalFontRenderContext.worldBucket(request).coerceAtMost(worlds.lastIndex)
        for (bucket in wanted downTo 1) {
            worlds[bucket].poll(bold, start = bucket == wanted)?.let {
                if (bucket != wanted) GlobalFontRenderContext.provisional = true
                return it
            }
        }
        if (wanted > 0) GlobalFontRenderContext.provisional = true
        return worlds[0].get(bold)
    }
}

internal class ContextualGlyph(
    private val screenVariants: LazyBakedValue<BakedStyles>,
    private val worldVariants: WorldStyles,
    private val request: GlobalFontRequest,
    private val bold: Boolean,
    private val x: Float,
    private val y: Float,
    private val color: Int,
    private val shadowColor: Int,
    private val semanticStyle: Style,
    private val boldOffset: Float,
    private val shadowOffset: Float,
    private val effectsSuppressed: Boolean,
) : TextRenderable.Styled {
    private var screenGlyph: TextRenderable.Styled? = null
    private var worldGlyph: TextRenderable.Styled? = null
    private var useWorld = false

    override fun render(matrix: Matrix4fc, consumer: VertexConsumer, light: Int, glowing: Boolean) {
        useWorld = !glowing
        active().render(matrix, consumer, light, glowing)
    }

    override fun renderType(mode: Font.DisplayMode): RenderType {
        useWorld = true
        return world().renderType(mode)
    }

    override fun textureView(): GpuTextureView {
        useWorld = false
        return screen().textureView()
    }

    override fun guiPipeline(): RenderPipeline {
        useWorld = false
        return screen().guiPipeline()
    }

    override fun style(): Style = semanticStyle
    override fun left(): Float = active().left()
    override fun top(): Float = active().top()
    override fun right(): Float = active().right()
    override fun bottom(): Float = active().bottom()

    private fun active(): TextRenderable.Styled = if (useWorld) world() else screen()

    private fun screen(): TextRenderable.Styled = screenGlyph ?: requireNotNull(createStyledGlyph(
        screenVariants.get(), request, bold, false,
        x, y, color, shadowColor, semanticStyle, boldOffset, shadowOffset, effectsSuppressed,
    )).also { screenGlyph = it }

    private fun world(): TextRenderable.Styled = worldGlyph ?: (createStyledGlyph(
        worldVariants.get(bold), request, bold, true,
        x, y, color, shadowColor, semanticStyle, boldOffset, shadowOffset, effectsSuppressed,
    ) ?: screen()).also { worldGlyph = it }
}

internal fun createScreenGlyph(
    variants: LazyBakedValue<BakedStyles>,
    request: GlobalFontRequest,
    bold: Boolean,
    x: Float,
    y: Float,
    color: Int,
    shadowColor: Int,
    style: Style,
    boldOffset: Float,
    shadowOffset: Float,
    effectsSuppressed: Boolean,
): TextRenderable.Styled? = createStyledGlyph(
    variants.get(), request, bold, false, x, y, color, shadowColor,
    style, boldOffset, shadowOffset, effectsSuppressed,
)

internal fun createStyledGlyph(
    variants: BakedStyles,
    request: GlobalFontRequest,
    bold: Boolean,
    world: Boolean,
    x: Float,
    y: Float,
    color: Int,
    shadowColor: Int,
    style: Style,
    boldOffset: Float,
    shadowOffset: Float,
    effectsSuppressed: Boolean,
): TextRenderable.Styled? {
    val selected = variants.select(bold)
    val renderStyle = RenderStyles.of(style, request.forceItalic, request.forceBold, variants.native)
    val effectiveBoldOffset = if (variants.native) 0f else boldOffset
    if (effectsSuppressed) {
        return selected.plainFill.createGlyph(
            x, y, color, shadowColor, renderStyle, effectiveBoldOffset, shadowOffset,
        )
    }

    val outline = request.outline
    val outlineColor = withSourceAlpha(outline.colorArgb, color)
    val hasOutline = outline.enabled && outline.width > 0f && outlineColor ushr 24 != 0
    val customShadow = request.shadow
    val customShadowColor = withSourceAlpha(customShadow.colorArgb, color)
    val hasCustomShadow = customShadow.enabled && customShadowColor ushr 24 != 0

    fun withoutSeparateOutline(glyph: BakedGlyph, shadowedGlyph: BakedGlyph?): TextRenderable.Styled? {
        if (!customShadow.enabled) {
            return glyph.createGlyph(x, y, color, shadowColor, renderStyle, effectiveBoldOffset, shadowOffset)
        }
        if (!hasCustomShadow) {
            return glyph.createGlyph(x, y, color, 0, renderStyle, effectiveBoldOffset, shadowOffset)
        }
        if (shadowedGlyph != null) {
            return shadowedGlyph.createGlyph(x, y, color, 0, renderStyle, effectiveBoldOffset, shadowOffset)
        }
        if (customShadow.offsetX == customShadow.offsetY) {
            return glyph.createGlyph(
                x, y, color, customShadowColor, renderStyle,
                effectiveBoldOffset, customShadow.offsetX,
            )
        }
        val main = glyph.createGlyph(
            x, y, color, 0, renderStyle, effectiveBoldOffset, shadowOffset,
        ) ?: return null
        val shadow = glyph.createGlyph(
            x + customShadow.offsetX, y + customShadow.offsetY, customShadowColor, 0,
            renderStyle, effectiveBoldOffset, shadowOffset,
        )
        return LayeredGlyph(shadow, null, main, world)
    }

    if (hasOutline && selected.integratedOutline) {
        return withoutSeparateOutline(selected.fill, selected.shadowedFill)
    }
    if (!hasOutline) {
        return withoutSeparateOutline(selected.plainFill, selected.shadowedPlainFill)
    }

    val main = selected.fill.createGlyph(
        x, y, color, 0, renderStyle, effectiveBoldOffset, shadowOffset,
    ) ?: return null
    val shadow = when {
        hasCustomShadow -> selected.fill.createGlyph(
            x + customShadow.offsetX, y + customShadow.offsetY, customShadowColor, 0,
            renderStyle, effectiveBoldOffset, shadowOffset,
        )
        shadowColor ushr 24 != 0 -> selected.fill.createGlyph(
            x + shadowOffset, y + shadowOffset, shadowColor, 0,
            renderStyle, effectiveBoldOffset, shadowOffset,
        )
        else -> null
    }
    val outlineGlyph = selected.outline?.createGlyph(
        x, y, outlineColor, 0, renderStyle, effectiveBoldOffset, shadowOffset,
    )
    return LayeredGlyph(shadow, outlineGlyph, main, world)
}

internal object RenderStyles {
    private var source: Style? = null
    private var flags = -1
    private var result: Style? = null

    fun of(style: Style, forceItalic: Boolean, forceBold: Boolean, native: Boolean): Style {
        val key = (if (forceItalic) 1 else 0) or (if (forceBold) 2 else 0) or (if (native) 4 else 0)
        if (style === source && key == flags) return result ?: style
        val italic = if (forceItalic && !style.isItalic) style.withItalic(true) else style
        val styled = when {
            native && italic.isBold -> italic.withBold(false)
            !native && forceBold && !italic.isBold -> italic.withBold(true)
            else -> italic
        }
        source = style
        flags = key
        result = styled
        return styled
    }
}

internal fun withSourceAlpha(effectColor: Int, sourceColor: Int): Int {
    val alpha = ((effectColor ushr 24) * (sourceColor ushr 24) + 127) / 255
    return effectColor and 0x00FFFFFF or (alpha shl 24)
}
