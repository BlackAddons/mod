package org.blackaddons.platform.font

import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.font.TrueTypeGlyphProvider
import com.mojang.blaze3d.font.UnbakedGlyph
import com.mojang.blaze3d.platform.TextureUtil
import it.unimi.dsi.fastutil.ints.IntSet
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.font.FontManager
import net.minecraft.client.gui.font.FontOption
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.font.providers.FreeTypeUtil
import net.minecraft.resources.Identifier
import org.blackaddons.api.cosmetics.NickFont
import org.blackaddons.core.font.NickFontFiles
import org.blackaddons.core.log.Log
import org.blackaddons.platform.Blackaddons
import org.blackaddons.platform.mixin.font.FontManagerAccessor
import org.blackaddons.platform.mixin.font.FontSetAccessor
import org.blackaddons.platform.mixin.font.MinecraftFontManagerAccessor
import org.blackaddons.platform.util.modId
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Minecraft font sets for the fonts cosmetic names use. One set per family, shared by everyone wearing it and holding
 * every subset any of them needed, with the game's font behind it for letters the font doesn't have. The number of sets
 * is capped and the oldest one goes first. A resource reload drops them, and the next name that asks builds its set
 * again. Client thread only.
 */
internal object NickFontSets {

    private const val MAX_FAMILIES = 16
    private const val NICK_PREFIX = "nick/"

    /**
     * Same as the global font: a TTF at size 11 is as tall as the game's letters. A name doesn't need more than 4x
     * oversampling, which keeps a family on the vanilla 256 atlas instead of the global font's 2048.
     */
    private const val SIZE = 11f
    private const val OVERSAMPLE = 4f

    private class Family(val id: Identifier, val set: FontSet, val providers: Map<String, GlyphProvider>) {
        var lastUsed = System.nanoTime()
    }

    private val families = HashMap<String, Family>()

    /** Every font a name asked for this session, so text naming one can build it when it's drawn. */
    private val known = ConcurrentHashMap<Identifier, NickFont>()

    /**
     * The font to set on a name: the family's id even before it's built (still downloading, failed, evicted, or a line
     * saved last session). `FontManagerMixin` draws it in the game's font until it's ready, so the text picks the font
     * up by itself instead of keeping whatever it was stored with.
     */
    fun idFor(font: NickFont): Identifier {
        val id = modId(NICK_PREFIX + font.familyKey)
        known[id] = font
        val mc = Minecraft.getInstance()
        if (!mc.isSameThread) return id
        val paths = NickFontFiles.settled(font) ?: return id
        val manager = (mc as MinecraftFontManagerAccessor).`blackaddons$getFontManager`() ?: return id
        val sets = (manager as FontManagerAccessor).`blackaddons$getFontSets`()

        var family = families[font.familyKey]
        if (family != null && sets[family.id] !== family.set) {
            families.remove(font.familyKey)
            family = null
        }
        val missing = paths.filterKeys { file -> family?.providers?.containsKey(file.url) != true }
        if (family == null || missing.isNotEmpty()) family = build(font, family, missing.mapKeys { it.key.url }, manager) ?: return id
        family.lastUsed = System.nanoTime()
        return family.id
    }

    fun isNickFont(id: Identifier): Boolean = id.namespace == Blackaddons.MOD_ID && id.path.startsWith(NICK_PREFIX)

    /**
     * Text drew [id] before its family was built: build it now if a name asked for that font this session and its files
     * are in. Otherwise a line decorated before the download finished stayed in the game's font until another name
     * happened to ask.
     */
    fun buildIfKnown(id: Identifier) {
        if (!Minecraft.getInstance().isSameThread) return
        known[id]?.let(::idFor)
    }

    private fun build(font: NickFont, old: Family?, missing: Map<String, Path>, manager: FontManager): Family? {
        val access = manager as FontManagerAccessor
        val sets = access.`blackaddons$getFontSets`()
        val loaded = missing.mapNotNull { (url, path) -> load(path)?.let { url to it } }.toMap()
        val providers = (old?.providers.orEmpty()) + loaded
        if (providers.isEmpty()) return null
        access.`blackaddons$getProvidersToClose`().addAll(loaded.values)

        val conditional = conditional(providers, sets[Minecraft.DEFAULT_FONT])
        val options = FontManagerAccessor.`blackaddons$getFontOptions`(Minecraft.getInstance().options)
        val id = old?.id ?: modId(NICK_PREFIX + font.familyKey)
        val set = if (old != null) old.set.also { it.reload(conditional, options) }
        else access.`blackaddons$createFontSet`(id, conditional, options).also { sets[id] = it }

        return Family(id, set, providers).also {
            families[font.familyKey] = it
            if (old == null) evict(access)
        }
    }

    /*
     * The global font swapped the default set's providers and closes the old ones right after. Every family still has
     * the old list as its fallback, so point them at the new one first or a missing letter reads a freed FreeType face.
     */
    fun defaultFontChanged(access: FontManagerAccessor) {
        val sets = access.`blackaddons$getFontSets`()
        families.values.removeIf { sets[it.id] !== it.set }
        if (families.isEmpty()) return
        val options = FontManagerAccessor.`blackaddons$getFontOptions`(Minecraft.getInstance().options)
        families.values.forEach { it.set.reload(conditional(it.providers, sets[Minecraft.DEFAULT_FONT]), options) }
    }

    private fun conditional(providers: Map<String, GlyphProvider>, default: FontSet?): List<GlyphProvider.Conditional> =
        providers.values.map { GlyphProvider.Conditional(it, FontOption.Filter.ALWAYS_PASS) } + fallback(default)

    // The game's own font goes behind ours, so a letter the subsets don't have gets drawn instead of shown as a box.
    private fun fallback(default: FontSet?): List<GlyphProvider.Conditional> =
        (default as? FontSetAccessor)?.`blackaddons$getAllProviders`().orEmpty()

    private fun evict(access: FontManagerAccessor) {
        while (families.size > MAX_FAMILIES) {
            val (key, oldest) = families.minBy { it.value.lastUsed }
            families.remove(key)
            access.`blackaddons$getFontSets`().remove(oldest.id, oldest.set)
            oldest.set.close()
            access.`blackaddons$getProvidersToClose`().removeAll(oldest.providers.values.toSet())
            oldest.providers.values.forEach(GlyphProvider::close)
        }
    }

    // Same as vanilla's TrueType provider definition, but from a file instead of a resource pack.
    private fun load(path: Path): GlyphProvider? {
        val memory = runCatching { Files.newInputStream(path).use(TextureUtil::readResource) }.getOrNull() ?: return null
        var face: FT_Face? = null
        return try {
            synchronized(FreeTypeUtil.LIBRARY_LOCK) {
                MemoryStack.stackPush().use { stack ->
                    val pointer = stack.mallocPointer(1)
                    FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0L, pointer), "Initializing nick font face")
                    face = FT_Face.create(pointer.get())
                }
                val loaded = requireNotNull(face)
                require(FreeType.FT_Get_Font_Format(loaded) == "TrueType") { "Not a TrueType font" }
                FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(loaded, FreeType.FT_ENCODING_UNICODE), "Finding nick font charmap")
                WholePixelAdvances(TrueTypeGlyphProvider(memory, loaded, SIZE, OVERSAMPLE, 0f, 0f, ""))
            }
        } catch (t: Throwable) {
            Log.main.warn("Nick font {} could not be loaded", path.fileName, t)
            synchronized(FreeTypeUtil.LIBRARY_LOCK) { face?.let(FreeType::FT_Done_Face) }
            MemoryUtil.memFree(memory)
            null
        }
    }
}

/**
 * A TTF advances by fractions of a pixel, so everything after a nick got drawn off the pixel grid, and the Global
 * Font's smoothly sampled glyphs smeared a column wider there. Whole pixels, like the game's own font.
 */
private class WholePixelAdvances(private val delegate: GlyphProvider) : GlyphProvider {
    override fun getGlyph(codePoint: Int): UnbakedGlyph? = delegate.getGlyph(codePoint)?.let(::WholePixelGlyph)

    override fun getSupportedGlyphs(): IntSet = delegate.supportedGlyphs

    override fun close() = delegate.close()
}

// The game lays out text from the baked glyph's info, so the baked glyph carries the rounded advance too.
private class WholePixelGlyph(private val delegate: UnbakedGlyph) : UnbakedGlyph {
    private val info = WholePixelInfo(delegate.info())

    override fun info(): GlyphInfo = info

    override fun bake(stitcher: UnbakedGlyph.Stitcher): BakedGlyph {
        val baked = delegate.bake(stitcher)
        return object : BakedGlyph by baked {
            override fun info(): GlyphInfo = this@WholePixelGlyph.info
        }
    }
}

private class WholePixelInfo(private val delegate: GlyphInfo) : GlyphInfo {
    private val advance = delegate.advance.roundToInt().toFloat()

    override fun getAdvance(): Float = advance

    override fun getAdvance(bold: Boolean): Float = advance + if (bold) boldOffset else 0f

    override fun getBoldOffset(): Float = delegate.boldOffset

    override fun getShadowOffset(): Float = delegate.shadowOffset
}
