package org.blackaddons.core.cosmetics

import com.google.gson.JsonObject
import org.blackaddons.api.cosmetics.NickStyle

/**
 * The `style` object sent over the wire: only the styles that are on, and no object when none are. Mods from before
 * styles ignore it, which is why it's its own object instead of a new color type.
 */
internal object NickStyles {

    fun parse(o: JsonObject?): NickStyle {
        if (o == null) return NickStyle.NONE
        return NickStyle(
            bold = on(o, "bold"),
            italic = on(o, "italic"),
            underlined = on(o, "underlined"),
            strikethrough = on(o, "strikethrough"),
            obfuscated = on(o, "obfuscated"),
        )
    }

    /** Null when the style is plain. */
    fun toJson(style: NickStyle): JsonObject? {
        if (style.isPlain) return null
        return JsonObject().apply {
            if (style.bold) addProperty("bold", true)
            if (style.italic) addProperty("italic", true)
            if (style.underlined) addProperty("underlined", true)
            if (style.strikethrough) addProperty("strikethrough", true)
            if (style.obfuscated) addProperty("obfuscated", true)
        }
    }

    private fun on(o: JsonObject, key: String): Boolean =
        o.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true
}
