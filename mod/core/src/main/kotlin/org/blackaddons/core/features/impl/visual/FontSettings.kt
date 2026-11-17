package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.OwnState
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.hud.HudFontContext
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.FontChoice
import org.blackaddons.api.util.I18n
import org.blackaddons.core.config.BlackaddonsConfig
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.font.*
import org.blackaddons.core.render.WorldRenderPrefs
import org.blackaddons.core.render.WorldTextFont
import org.blackaddons.core.ui.screen.GlobalFontScreen
import org.blackaddons.core.ui.screen.portable.PortableExportModal

@AlwaysActive
object FontSettings : Feature(
    description = "Fonts for Minecraft's own text, the BlackAddons HUD and text drawn in the world.",
    name = "Fonts",
), OwnState {
    private val tr = I18n.scope("blackaddons.global_font_screen")

    override val section = Categories.Visual.World

    @Suppress("unused")
    private val configure by button("Configure", action = { UiManager.push(GlobalFontScreen()) }, label = "Open")
        .withDescription("Choose and preview the Minecraft, HUD and world fonts and how each is drawn.")

    val minecraft = NativeFont(FontSlot.MINECRAFT) { tr("status_disabled", "Disabled") }
    val world = NativeFont(FontSlot.WORLD) { tr("status_applied", "Applied") }

    private var stored: FontsDocument? = null
    internal val document: FontsDocument
        get() = stored ?: GlobalFontStore.load(::migrateLegacy).also { stored = it }

    private var hudFont: Font? = null
    private val overrideFonts = HashMap<String, Font>()

    override fun init() {
        menuAction("Share Fonts", icon = LucideIcons.share) { PortableExportModal.share(translatedName, FontPortable.export()) }
        menuAction("Paste Fonts", icon = LucideIcons.clipboardPaste) { PortableImports.open(FontPortable, Platform.clipboard.paste()) }
        HudFontContext.globalFont = { hudFont ?: document.hud.let { it.font.hudFont(it.draft) }.also { hudFont = it } }
        HudFontContext.installedFont = ::overrideFont
        WorldRenderPrefs.worldFont = { if (document.world.font.isFile) WorldTextFont.CUSTOM else WorldTextFont.VANILLA }
    }

    override fun onEnable() {
        minecraft.apply(document.minecraft.native())
        world.apply(document.world.native())
    }

    internal fun apply(next: FontsDocument) {
        val previous = document
        store(next)
        if (next.minecraft.native() != previous.minecraft.native()) minecraft.apply(next.minecraft.native())
        if (next.world.native() != previous.world.native()) world.apply(next.world.native())
    }

    private fun GlobalFontDocument.native() = takeIf { it.font.isFile }

    /** TODO: delete on release, together with `GlobalFontStore.migrate`. */
    private fun migrateLegacy(): Boolean? {
        FeatureManager.hudByName("Performance HUD", null)
            ?.takeIf { it.fontOverride == FontChoice.JETBRAINS_MONO.name }
            ?.fontOverride = FontChoice.INHERIT.name
        return BlackaddonsConfig.removedFeatureEnabled("GlobalFont")
    }

    internal fun applyShared(next: FontsDocument) {
        val missing = next.fonts.firstOrNull {
            it.key.startsWith(FontCatalog.FONTSOURCE_KEY_PREFIX) && FontCatalog.paths(it.cacheKeys) == null
        } ?: return apply(next)
        val preparing = tr("preparing_font", "Preparing %s...", missing.displayName)
        listOf(minecraft, world).forEach { it.show(preparing, applying = true) }
        FontCatalog.install(missing.key.removePrefix(FontCatalog.FONTSOURCE_KEY_PREFIX)) { result ->
            Platform.scheduler.schedule(0) {
                result.onSuccess { applyShared(next.replacing(missing.key, it)) }.onFailure {
                    val failed = tr("status_apply_failed", "Could not apply: %s", it.message.orEmpty())
                    listOf(minecraft, world).forEach { slot -> slot.show(failed, applying = false) }
                }
            }
        }
    }

    internal fun uninstalled(font: InstalledFont) {
        overrideFonts.remove(font.key)
        if (document.fonts.any { it.key == font.key }) apply(document.replacing(font.key, InstalledFont.BUNDLED))
    }

    override fun resetOwnState() = apply(FontsDocument.DEFAULT)

    private fun store(document: FontsDocument) {
        stored = document
        hudFont = null
        overrideFonts.clear()
        GlobalFontStore.save(document)
    }

    private fun overrideFont(key: String): Font? = overrideFonts[key]
        ?: FontCatalog.installed().firstOrNull { it.key == key }?.uiFont()?.also { overrideFonts[key] = it }

    class NativeFont internal constructor(private val slot: FontSlot, private val idle: () -> String) {
        @Volatile
        var status: String = idle()
            private set

        @Volatile
        var applying: Boolean = false
            private set

        internal fun show(status: String, applying: Boolean) {
            this.status = status
            this.applying = applying
        }

        internal fun apply(target: GlobalFontDocument?) {
            if (target == null) return restore()
            val (font, draft) = target
            val paths = FontCatalog.paths(font.cacheKeys)
            val missingCachedFont = !font.isBundled && paths == null
            val useBundled = font.isBundled || missingCachedFont
            val request = draft.toRequest(
                files = if (useBundled) emptyList() else requireNotNull(paths),
                bundledResource = if (useBundled) InstalledFont.BUNDLED_RESOURCE else null,
            )
            val missing = tr("status_cached_missing", "Cached font missing - using JetBrains Mono")
            show(if (missingCachedFont) missing else tr("applying", "Applying..."), applying = true)
            GlobalFontBridge.apply(slot, request) { result ->
                status = when (result) {
                    GlobalFontApplyResult.Applied -> if (missingCachedFont) missing else tr("status_applied", "Applied")
                    is GlobalFontApplyResult.Failed -> tr("status_apply_failed", "Could not apply: %s", result.message)
                }
                applying = false
            }
        }

        private fun restore() {
            show(tr("status_restoring", "Restoring Minecraft font..."), applying = true)
            GlobalFontBridge.restore(slot) { result ->
                status = when (result) {
                    GlobalFontApplyResult.Applied -> idle()
                    is GlobalFontApplyResult.Failed -> tr("status_restore_failed", "Could not restore: %s", result.message)
                }
                applying = false
            }
        }
    }
}
