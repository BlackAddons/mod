package org.blackaddons.platform.font

import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.platform.TextureUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.font.FontOption
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.providers.FreeTypeUtil
import net.minecraft.resources.Identifier
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.font.GlobalFontApplyResult
import org.blackaddons.core.font.GlobalFontRequest
import org.blackaddons.core.font.GlobalFontRetryGate
import org.blackaddons.core.log.Log
import org.blackaddons.platform.mixin.font.FontManagerAccessor
import org.blackaddons.platform.mixin.font.FontSetAccessor
import org.blackaddons.platform.mixin.font.MinecraftFontManagerAccessor
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.io.InputStream
import java.nio.file.Files
import kotlin.math.ceil

internal class NativeFontSlot(private val fontId: Identifier) {
    private val tr = I18n.scope("blackaddons.global_font_screen")

    private val retryGate = GlobalFontRetryGate()

    @Volatile private var request: GlobalFontRequest? = null
    @Volatile private var callback: (GlobalFontApplyResult) -> Unit = {}
    @Volatile private var building = false

    @Volatile
    var fontRevision = 0
        private set

    @Volatile
    var atlasSize = VANILLA_ATLAS_SIZE
        private set

    private var installedSet: FontSet? = null
    private var observedSet: FontSet? = null
    private var installedScreenBase = 0f
    private var observedGuiScale = 0
    private var activeProviders: List<StyledGlyphProvider> = emptyList()
    private val atlasPages = HashSet<String>()
    private var grayscaleAtlasPages = 0
    private var coloredAtlasPages = 0

    val enabled: Boolean get() = request != null

    fun apply(next: GlobalFontRequest, done: (GlobalFontApplyResult) -> Unit) {
        request = next
        callback = done
        retryGate.requestChanged()
        buildLatest()
    }

    fun restore(done: (GlobalFontApplyResult) -> Unit) {
        request = null
        callback = done
        retryGate.requestChanged()
        Minecraft.getInstance().execute {
            if (activeProviders.isEmpty()) return@execute done(GlobalFontApplyResult.Applied)
            runCatching { restoreProviders() }
                .onSuccess { done(GlobalFontApplyResult.Applied) }
                .onFailure {
                    Log.main.warn("Font {} could not be restored", fontId, it)
                    done(GlobalFontApplyResult.Failed(it.message ?: tr("atlas_restore_failed", "font atlas could not be restored")))
                }
        }
    }

    fun owns(textureName: String): Boolean = request != null && textureName.startsWith("$fontId/")

    fun recordAtlasPage(textureName: String, colored: Boolean) {
        synchronized(atlasPages) {
            if (!atlasPages.add(textureName)) return
            if (colored) coloredAtlasPages++ else grayscaleAtlasPages++
            Log.main.debug(
                "Native font atlas page created: {} ({}; {} grayscale, {} color)",
                textureName, if (colored) "color" else "grayscale",
                grayscaleAtlasPages, coloredAtlasPages,
            )
        }
    }

    fun worldBucket(pixelsPerUnit: Float): Int = if (enabled) request?.worldBucket(pixelsPerUnit) ?: 0 else 0

    fun tick() {
        val set = fontSet() ?: return
        if (observedSet !== set) {
            observedSet = set

            installedSet = null
            activeProviders = emptyList()
            fontRevision++
            retryGate.environmentChanged()
        }
        val guiScale = guiScale()
        if (observedGuiScale != guiScale) {
            observedGuiScale = guiScale
            retryGate.environmentChanged()
        }
        if (installedSet === set && request?.screenBase(guiScale) == installedScreenBase) return
        if (!building && request != null && retryGate.canAttempt()) buildLatest()
    }

    private fun buildLatest() {
        val wanted = request ?: return
        if (fontSet() == null || building || !retryGate.canAttempt()) return
        building = true
        val token = retryGate.current()
        val guiScale = guiScale()
        Workers.start("font-provider") {
            val built = runCatching { buildProviders(wanted, guiScale) }
            Minecraft.getInstance().execute {
                building = false
                if (token != retryGate.current()) {
                    built.getOrNull()?.forEach(StyledGlyphProvider::close)
                    buildLatest()
                    return@execute
                }
                built.fold(
                    onSuccess = { providers ->
                        runCatching { installProviders(token, providers, guiScale) }
                            .onFailure {
                                providers.forEach(StyledGlyphProvider::close)
                                retryGate.markFailed(token)
                                Log.main.warn("Font {} could not be installed", fontId, it)
                                callback(GlobalFontApplyResult.Failed(it.message ?: tr("atlas_rebuild_failed", "font atlas could not be rebuilt")))
                            }
                    },
                    onFailure = {
                        retryGate.markFailed(token)
                        Log.main.warn("Font {} could not be prepared", fontId, it)
                        callback(GlobalFontApplyResult.Failed(it.message ?: tr("invalid_font", "invalid font")))
                    },
                )
            }
        }
    }

    private fun installProviders(token: Int, providers: List<StyledGlyphProvider>, guiScale: Int) {
        val mc = Minecraft.getInstance()
        val manager = fontManager(mc)
        val set = fontSet() ?: error(tr("default_not_ready", "Minecraft's default font is not ready"))
        val managerAccess = manager as FontManagerAccessor
        val base = baseProviders(set)
        val options = FontManagerAccessor.`blackaddons$getFontOptions`(mc.options)
        val styledFallbacks = base.map { conditional ->
            val provider = conditional.provider()
            GlyphProvider.Conditional(
                StyledGlyphProvider(
                    List(SCREEN_SCALE_BUCKETS.size) { provider }, listOf(provider),
                    requireNotNull(request), ownsDelegate = false,
                ),
                conditional.filter(),
            )
        }
        val conditional = providers.map { GlyphProvider.Conditional(it, FontOption.Filter.ALWAYS_PASS) } + styledFallbacks

        val old = activeProviders
        val owned = managerAccess.`blackaddons$getProvidersToClose`()
        atlasSize = atlasSizeFor(requireNotNull(request), guiScale)
        resetAtlasStats()
        set.reload(conditional, options)
        fontRevision++
        NickFontSets.defaultFontChanged(managerAccess)
        owned.removeAll(old.toSet())
        owned.addAll(providers)
        old.forEach(StyledGlyphProvider::close)

        activeProviders = providers
        installedSet = set
        installedScreenBase = requireNotNull(request).screenBase(guiScale)
        observedSet = set
        if (token == retryGate.current()) callback(GlobalFontApplyResult.Applied)
    }

    private fun restoreProviders() {
        val mc = Minecraft.getInstance()
        val manager = fontManager(mc) as FontManagerAccessor
        val set = fontSet() ?: error(tr("default_not_ready", "Minecraft's default font is not ready"))
        val old = activeProviders
        resetAtlasStats()
        set.reload(baseProviders(set), FontManagerAccessor.`blackaddons$getFontOptions`(mc.options))
        fontRevision++
        NickFontSets.defaultFontChanged(manager)
        manager.`blackaddons$getProvidersToClose`().removeAll(old.toSet())
        old.forEach(StyledGlyphProvider::close)
        activeProviders = emptyList()
        installedSet = null
    }

    private fun baseProviders(set: FontSet): List<GlyphProvider.Conditional> =
        (set as FontSetAccessor).`blackaddons$getAllProviders`().mapNotNull { conditional ->
            val provider = conditional.provider()
            if (provider is StyledGlyphProvider && provider.ownsDelegate) return@mapNotNull null
            GlyphProvider.Conditional(
                if (provider is StyledGlyphProvider) provider.screenDelegate else provider,
                conditional.filter(),
            )
        }

    private fun buildProviders(request: GlobalFontRequest, guiScale: Int): List<StyledGlyphProvider> {
        val streams = streams(request.files, request.bundledResource)
        val providers = ArrayList<StyledGlyphProvider>(streams.size)
        try {
            for (open in streams) {
                var regular: ProviderFamily? = null
                try {
                    regular = loadFamily(open, request, guiScale)
                    providers += StyledGlyphProvider(
                        regular.screens, regular.worlds,
                        request, ownsDelegate = true,
                    )
                } catch (t: Throwable) {
                    regular?.close()
                    throw t
                }
            }
            return providers
        } catch (t: Throwable) {
            providers.forEach(StyledGlyphProvider::close)
            throw t
        }
    }

    private fun streams(
        files: List<java.nio.file.Path>,
        bundledResource: String?,
    ): List<() -> InputStream> = when {
        files.isNotEmpty() -> files.map { path -> { Files.newInputStream(path) } }
        bundledResource != null -> listOf({
            MinecraftGlobalFonts::class.java.getResourceAsStream(bundledResource)
                ?: error(tr("bundled_missing", "Bundled font is missing"))
        })
        else -> error(tr("no_files_selected", "No font files were selected"))
    }

    private fun loadFamily(open: () -> InputStream, request: GlobalFontRequest, guiScale: Int): ProviderFamily {
        val memory = SharedFontMemory(open().use(TextureUtil::readResource))
        val loaded = LinkedHashMap<Float, GlyphProvider>()
        return try {
            fun provider(oversample: Float): GlyphProvider =
                loaded.getOrPut(oversample) { loadOutlineFont(memory, request, oversample) }

            ProviderFamily(
                SCREEN_SCALE_BUCKETS.map { provider(request.screenOversample(it, guiScale)) },
                request.worldOversamples.map(::provider),
                loaded.values.toList(),
            )
        } catch (t: Throwable) {
            loaded.values.forEach(GlyphProvider::close)
            throw t
        } finally {
            memory.release()
        }
    }

    private fun loadOutlineFont(memory: SharedFontMemory, request: GlobalFontRequest, oversample: Float): GlyphProvider {
        var face: FT_Face? = null
        try {
            synchronized(FreeTypeUtil.LIBRARY_LOCK) {
                MemoryStack.stackPush().use { stack ->
                    val pointer = stack.mallocPointer(1)
                    FreeTypeUtil.assertError(
                        FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory.buffer, 0L, pointer),
                        tr("initializing_face", "Initializing global font face"),
                    )
                    face = FT_Face.create(pointer.get())
                }
                val loadedFace = requireNotNull(face)
                require(FreeType.FT_IS_SCALABLE(loadedFace)) {
                    tr("font_not_scalable", "Bitmap-only fonts are not supported")
                }
                FreeTypeUtil.assertError(
                    FreeType.FT_Select_Charmap(loadedFace, FreeType.FT_ENCODING_UNICODE),
                    tr("finding_charmap", "Finding global font Unicode charmap"),
                )
                return NativeFreeTypeGlyphProvider(
                    memory, loadedFace, request.rasterSize, oversample,
                    request.shiftX, request.shiftY,
                    request.outline.width.takeIf { request.outline.enabled } ?: 0f,
                    request.outline.colorArgb,
                    request.shadow.colorArgb.takeIf { request.shadow.enabled } ?: 0,
                    request.shadow.offsetX,
                    request.shadow.offsetY,
                )
            }
        } catch (t: Throwable) {
            synchronized(FreeTypeUtil.LIBRARY_LOCK) {
                face?.let { FreeType.FT_Done_Face(it) }
            }
            throw t
        }
    }

    private fun fontSet(): FontSet? {
        val mc = Minecraft.getInstance()
        val manager = fontManager(mc) as? FontManagerAccessor ?: return null
        return manager.`blackaddons$getFontSets`()?.get(fontId)
    }

    private fun guiScale() = Minecraft.getInstance().window.guiScale

    private fun fontManager(mc: Minecraft) = (mc as? MinecraftFontManagerAccessor)?.`blackaddons$getFontManager`()

    private fun resetAtlasStats() = synchronized(atlasPages) {
        atlasPages.clear()
        grayscaleAtlasPages = 0
        coloredAtlasPages = 0
    }

    private fun atlasSizeFor(request: GlobalFontRequest, guiScale: Int): Int {
        val oversampling = ceil(maxOf(request.screenBase(guiScale), request.oversampleWorld)).toInt().coerceAtLeast(1)
        val requested = ATLAS_PIXELS_PER_OVERSAMPLE * oversampling
        return requested.coerceAtMost(MAX_CUSTOM_ATLAS_SIZE)
    }

    private data class ProviderFamily(
        val screens: List<GlyphProvider>,
        val worlds: List<GlyphProvider>,
        private val owned: List<GlyphProvider>,
    ) {
        fun close() = owned.forEach(GlyphProvider::close)
    }

    private companion object {
        val SCREEN_SCALE_BUCKETS = listOf(1, 2, 4)
        const val ATLAS_PIXELS_PER_OVERSAMPLE = 512
        const val MAX_CUSTOM_ATLAS_SIZE = 2048
    }
}

internal const val VANILLA_ATLAS_SIZE = 256
