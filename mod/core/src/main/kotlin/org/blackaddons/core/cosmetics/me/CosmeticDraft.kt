package org.blackaddons.core.cosmetics.me

import com.google.gson.JsonObject
import org.blackaddons.api.cosmetics.*
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.core.cosmetics.GradientStops
import org.blackaddons.core.cosmetics.NickStyles
import org.blackaddons.core.cosmetics.store.CosmeticJson
import kotlin.math.abs

internal enum class ColorMode {
    VANILLA,
    SOLID,
    GRADIENT,
    CHROMA;

    val wire: String
        get() = when (this) {
            VANILLA -> "Vanilla"
            SOLID -> "Solid"
            GRADIENT -> "Gradient"
            CHROMA -> "Chroma"
        }

    companion object {
        fun fromWire(s: String?): ColorMode = when (s) {
            "Solid" -> SOLID
            "Gradient" -> GRADIENT
            "Chroma" -> CHROMA
            else -> VANILLA
        }
    }
}

/**
 * Editable copy of the player's cosmetic for `/v1/me/cosmetic`. Holds raw wire fields, so a save without changes does
 * nothing (`perCharacter` keeps the API polarity). The server owns [enabled]. [displayName] only changes through a name
 * request.
 */
internal class CosmeticDraft(
    var displayName: String?,
    val enabled: Boolean,
    var mode: ColorMode,
    var rgb: Int,
    /** Sorted when serialized. */
    val stops: MutableList<ColorStop>,
    var perCharacter: Boolean,
    var animated: Boolean,
    var speed: Float,
    var scaleX: Float,
    var scaleY: Float,
    var scaleZ: Float,
    /** Only admins set obfuscated: it shows in the preview but is never sent. */
    var style: NickStyle = NickStyle.NONE,
    /** Preview only. A font gets put on through its own endpoint, never by a save. */
    var font: NickFont? = null,
) {
    fun copyOf(): CosmeticDraft = CosmeticDraft(
        displayName, enabled, mode, rgb, stops.toMutableList(),
        perCharacter, animated, speed, scaleX, scaleY, scaleZ, style, font,
    )

    fun loadFrom(other: CosmeticDraft) {
        mode = other.mode
        rgb = other.rgb
        stops.clear(); stops.addAll(other.stops)
        perCharacter = other.perCharacter
        animated = other.animated
        speed = other.speed
        scaleX = other.scaleX; scaleY = other.scaleY; scaleZ = other.scaleZ
        style = other.style
    }

    fun matches(baseline: CosmeticDraft): Boolean =
        mode == baseline.mode && rgb == baseline.rgb && stops == baseline.stops &&
            perCharacter == baseline.perCharacter && animated == baseline.animated &&
            speed == baseline.speed && scaleX == baseline.scaleX &&
            scaleY == baseline.scaleY && scaleZ == baseline.scaleZ && style == baseline.style

    /** Preview mapping, same as [CosmeticJson], including `wholeTextGradient = !perCharacter`. */
    fun toPreviewCosmetic(): Cosmetic = Cosmetic(
        display = displayName,
        name = ColorSpec(
            solidColor = if (mode == ColorMode.SOLID) rgb and 0xFFFFFF else null,
            gradient = if (mode == ColorMode.GRADIENT) stops.sortedBy { it.fraction } else emptyList(),
            chroma = mode == ColorMode.CHROMA,
            animated = animated,
            speed = speed,
            wholeTextGradient = !perCharacter,
        ),
        size = Scale(scaleX, scaleY, scaleZ),
        style = style,
        font = font,
    )

    fun toUpdateJson(): JsonObject {
        val color = JsonObject().apply {
            addProperty("type", mode.wire)
            when (mode) {
                ColorMode.SOLID -> addProperty("rgb", rgb and 0xFFFFFF)
                ColorMode.GRADIENT -> add("gradient", GradientStops.toJson(stops))
                else -> {}
            }
            // Non-nullable in the request schema.
            addProperty("perCharacter", perCharacter)
            addProperty("animated", animated)
            addProperty("speed", speed)
        }
        val scale = JsonObject().apply {
            addProperty("x", scaleX); addProperty("y", scaleY); addProperty("z", scaleZ)
        }
        // Every flag, including the ones that are off: the server reads a missing style as "leave it".
        val style = JsonObject().apply {
            addProperty("bold", style.bold)
            addProperty("italic", style.italic)
            addProperty("underlined", style.underlined)
            addProperty("strikethrough", style.strikethrough)
        }
        return JsonObject().apply { add("color", color); add("scale", scale); add("style", style) }
    }

    companion object {
        fun fromDto(o: JsonObject): CosmeticDraft {
            val color = o.get("color") as? JsonObject
            val mode = ColorMode.fromWire(color?.let { str(it, "type") })
            val scale = o.get("scale") as? JsonObject
            return CosmeticDraft(
                displayName = str(o, "displayName"),
                enabled = bool(o, "enabled"),
                mode = mode,
                rgb = (color?.let { int(it, "rgb") } ?: 0xFFFFFF) and 0xFFFFFF,
                stops = GradientStops.parse(color?.get("gradient") as? JsonObject).toMutableList(),
                perCharacter = color?.let { bool(it, "perCharacter") } ?: false,
                animated = color?.let { bool(it, "animated") } ?: false,
                speed = color?.let { float(it, "speed") } ?: 1f,
                // A negative width or depth saved before side mirrors were removed gets edited as its size (-1 -> 1).
                scaleX = abs(scale?.let { float(it, "x") } ?: 1f),
                scaleY = scale?.let { float(it, "y") } ?: 1f,
                scaleZ = abs(scale?.let { float(it, "z") } ?: 1f),
                style = NickStyles.parse(o.get("style") as? JsonObject),
                font = CosmeticJson.parseFont(o.get("font") as? JsonObject),
            )
        }

        private fun str(o: JsonObject, k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asString
        private fun bool(o: JsonObject, k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        private fun int(o: JsonObject, k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asInt
        private fun float(o: JsonObject, k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asFloat
    }
}
