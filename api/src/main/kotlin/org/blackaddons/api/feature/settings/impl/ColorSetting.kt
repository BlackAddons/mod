package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.blackaddons.api.config.Savable
import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.util.color.Chroma
import org.blackaddons.api.util.color.toColorOrNull
import org.blackaddons.api.util.color.toHex
import java.awt.Color

/** Chroma for `Render.chromaText`: [ALL] as one color, [PER_CHAR] per glyph. Fills always use [ColorSetting.value]. */
enum class ChromaMode { ALL, PER_CHAR }

/**
 * Color with optional chroma. [value] is live (animated when [chroma] is on); [baseColor] is what the user
 * picked. Use [colorAt] for a rainbow across several elements.
 */
open class ColorSetting(
    name: String,
    default: Color,
    val allowAlpha: Boolean = true,
    id: String = name,
    /** Offers per-character chroma in the picker; set for text colors only. */
    val textChroma: Boolean = false,
) : Setting<Color>(id, name, default), Savable {

    // The stored value. [value] puts the chroma hue on top of it.
    private var base: Color
        get() = super.value
        set(v) { super.value = v }

    var chroma: Boolean = false

    /** Hue cycles per second. */
    var chromaSpeed: Float = 0.5f

    var chromaMode: ChromaMode = ChromaMode.ALL

    val baseColor: Color get() = base

    var defaultChroma: Boolean = false
        private set
    var defaultChromaSpeed: Float = 0.5f
        private set
    var defaultChromaMode: ChromaMode = ChromaMode.ALL
        private set

    override fun onRegister() {
        defaultChroma = chroma
        defaultChromaSpeed = chromaSpeed
        defaultChromaMode = chromaMode
    }

    /** Chroma hue when [chroma] is on, else [baseColor]. Assigning sets the base. */
    override var value: Color
        get() = if (chroma) chromaColor(0f) else base
        set(v) { base = v }

    override fun reset() {
        super.reset()
        chroma = defaultChroma
        chromaSpeed = defaultChromaSpeed
        chromaMode = defaultChromaMode
    }

    /** [offset] is in hue cycles (usually 0..1). */
    fun colorAt(offset: Float): Color = if (chroma) chromaColor(offset) else base

    private fun chromaColor(offset: Float): Color =
        Chroma.color(base, chromaSpeed, offset, allowAlpha)

    /** Bare hex string while chroma is off; an object with chroma parameters otherwise. */
    override fun write(): JsonElement {
        val hex = hexOf(base)
        if (!chroma) return JsonPrimitive(hex)
        return JsonObject().apply {
            addProperty("color", hex)
            addProperty("chroma", true)
            addProperty("chromaSpeed", chromaSpeed)
            addProperty("chromaMode", chromaMode.name)
        }
    }

    override fun read(element: JsonElement?) {
        when {
            element == null -> return
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.get("color")?.takeIf { it.isJsonPrimitive }?.let { parseColor(it.asJsonPrimitive)?.let { c -> base = c } }
                chroma = obj.get("chroma")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                obj.get("chromaSpeed")?.takeIf { it.isJsonPrimitive }?.asFloat?.let { chromaSpeed = it }
                obj.get("chromaMode")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.let { runCatching { ChromaMode.valueOf(it) }.getOrNull() }
                    ?.let { chromaMode = it }
            }
            element.isJsonPrimitive -> parseColor(element.asJsonPrimitive)?.let {
                base = it
                chroma = false
            }
        }
    }

    private fun hexOf(c: Color): String = c.toHex(allowAlpha)

    /** Hex string, or a legacy raw int. */
    private fun parseColor(p: JsonPrimitive): Color? = when {
        p.isString -> p.asString.toColorOrNull()
        p.isNumber -> Color(p.asInt, allowAlpha)
        else -> null
    }
}
