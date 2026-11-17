package org.blackaddons.core.font

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.color.toColorOrNull
import org.blackaddons.api.util.color.toHex
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.api.util.io.array
import org.blackaddons.api.util.io.bool
import org.blackaddons.api.util.io.child
import org.blackaddons.api.util.io.double
import org.blackaddons.api.util.io.str
import org.blackaddons.core.font.GlobalFontStore.isLegacy
import org.blackaddons.core.log.Log
import java.awt.Color

internal data class GlobalFontDocument(val font: InstalledFont, val draft: GlobalFontDraft) {
    companion object {
        val DEFAULT = GlobalFontDocument(InstalledFont.BUNDLED, GlobalFontDraft.DEFAULT)
        val WORLD_DEFAULT = GlobalFontDocument(InstalledFont.BUNDLED, GlobalFontDraft.WORLD_DEFAULT)
    }
}

internal data class FontsDocument(
    val minecraft: GlobalFontDocument,
    val hud: GlobalFontDocument,
    val world: GlobalFontDocument,
) {
    val fonts: List<InstalledFont> get() = listOf(minecraft.font, hud.font, world.font)

    fun replacing(key: String, font: InstalledFont) = FontsDocument(minecraft.with(key, font), hud.with(key, font), world.with(key, font))

    private fun GlobalFontDocument.with(key: String, font: InstalledFont) = if (this.font.key == key) copy(font = font) else this

    companion object {
        val DEFAULT = FontsDocument(
            GlobalFontDocument(InstalledFont.VANILLA, GlobalFontDraft.DEFAULT),
            GlobalFontDocument.DEFAULT,
            GlobalFontDocument.WORLD_DEFAULT,
        )
    }
}

internal object GlobalFontStore {
    private val path get() = Platform.configDir.resolve("font.json")
    private const val AUTO_2D_KEY = "auto2d"

    fun load(legacyEnabled: () -> Boolean?): FontsDocument = runCatching {
        val json = Disk.read(path)?.let { JsonParser.parseString(it).asJsonObject }
        if (json != null && !isLegacy(json)) return@runCatching readAll(json)
        migrate(json, legacyEnabled()).also(::save)
    }.onFailure {
        Log.main.warn("Could not read {}, using the default font", path, it)
    }.getOrNull() ?: FontsDocument.DEFAULT

    fun save(document: FontsDocument) {
        runCatching { Disk.write(path, Gsons.pretty.toJson(writeAll(document))) }
            .onFailure { Log.main.error("Failed to save {}", path, it) }
    }

    fun writeAll(document: FontsDocument): JsonObject = JsonObject().apply {
        addProperty(AUTO_2D_KEY, true)
        add("minecraft", write(document.minecraft))
        add("hud", write(document.hud))
        add("world", write(document.world))
    }

    fun readAll(json: JsonObject): FontsDocument {
        val default = FontsDocument.DEFAULT
        val document = FontsDocument(
            minecraft = json.child("minecraft")?.let { read(it, default.minecraft) } ?: default.minecraft,
            hud = json.child("hud")?.let { read(it, default.hud) } ?: default.hud,
            world = json.child("world")?.let { read(it, default.world) } ?: default.world,
        )
        return if (json.has(AUTO_2D_KEY)) document else document.withAuto2d()
    }

    /* Files from before Auto 2D oversampling switch to it once, the next save writes the marker. */
    private fun FontsDocument.withAuto2d() =
        copy(minecraft = minecraft.copy(draft = minecraft.draft.copy(oversample2d = GlobalFontDraft.AUTO_OVERSAMPLE)))

    /** TODO: delete on release, together with [isLegacy], `BlackaddonsConfig.removedFeatureEnabled` and their test. */
    fun migrate(json: JsonObject?, enabled: Boolean?): FontsDocument {
        val minecraft = json?.let { read(it) } ?: GlobalFontDocument.DEFAULT
        return FontsDocument.DEFAULT.copy(minecraft = if (enabled == true) minecraft else minecraft.copy(font = InstalledFont.VANILLA)).withAuto2d()
    }

    private fun isLegacy(json: JsonObject) = !json.has("minecraft") && json.has("font")

    fun write(document: GlobalFontDocument): JsonObject = JsonObject().apply {
        val (font, draft) = document
        add("font", writeFont(font))
        addProperty("rasterSize", draft.rasterSize)
        addProperty("oversample2d", draft.oversample2d)
        addProperty("oversampleWorld", draft.oversampleWorld)
        addProperty("shiftX", draft.shiftX)
        addProperty("shiftY", draft.shiftY)
        addProperty("letterSpacing", draft.letterSpacing)
        addProperty("bold", draft.forceBold)
        addProperty("italic", draft.forceItalic)
        add("shadow", JsonObject().apply {
            addProperty("enabled", draft.shadowEnabled)
            addProperty("color", draft.shadowColor.toHex())
            addProperty("x", draft.shadowX)
            addProperty("y", draft.shadowY)
        })
        add("outline", JsonObject().apply {
            addProperty("enabled", draft.outlineEnabled)
            addProperty("color", draft.outlineColor.toHex())
            addProperty("width", draft.outlineWidth)
        })
    }

    fun read(json: JsonObject, defaults: GlobalFontDocument = GlobalFontDocument.DEFAULT): GlobalFontDocument {
        val default = defaults.draft
        val shadow = json.child("shadow")
        val outline = json.child("outline")
        val draft = GlobalFontDraft(
            rasterSize = json.double("rasterSize", default.rasterSize),
            oversample2d = json.double("oversample2d", default.oversample2d),
            oversampleWorld = json.double("oversampleWorld", default.oversampleWorld),
            shiftX = json.double("shiftX", default.shiftX),
            shiftY = json.double("shiftY", default.shiftY),
            letterSpacing = json.double("letterSpacing", default.letterSpacing),
            forceBold = json.bool("bold", default.forceBold),
            forceItalic = json.bool("italic", default.forceItalic),
            shadowEnabled = shadow.bool("enabled", default.shadowEnabled),
            shadowColor = shadow.color("color") ?: default.shadowColor,
            shadowX = shadow.double("x", default.shadowX),
            shadowY = shadow.double("y", default.shadowY),
            outlineEnabled = outline.bool("enabled", default.outlineEnabled),
            outlineColor = outline.color("color") ?: default.outlineColor,
            outlineWidth = outline.double("width", default.outlineWidth),
        ).coerced()
        return GlobalFontDocument(readFont(json.child("font"), defaults.font), draft)
    }

    private fun writeFont(font: InstalledFont) = JsonObject().apply {
        addProperty("key", font.key)
        addProperty("name", font.displayName)
        add("cacheKeys", JsonArray().apply { font.cacheKeys.forEach(::add) })
    }

    private fun readFont(json: JsonObject?, default: InstalledFont): InstalledFont {
        val key = json.str("key")?.takeIf(String::isNotBlank) ?: return default
        BUILT_IN.firstOrNull { it.key == key }?.let { return it }
        val cacheKeys = json.array("cacheKeys")
            ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            .orEmpty()
        return InstalledFont(key, json.str("name")?.takeIf(String::isNotBlank) ?: key, cacheKeys)
    }

    private fun JsonObject?.color(key: String): Color? = str(key)?.toColorOrNull()

    private val BUILT_IN = listOf(InstalledFont.BUNDLED, InstalledFont.VANILLA)
}
