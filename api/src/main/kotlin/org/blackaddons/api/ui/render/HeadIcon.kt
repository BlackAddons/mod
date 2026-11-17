package org.blackaddons.api.ui.render

import com.google.gson.JsonParser
import java.util.Base64

/** Draws a face plus hat from a base64 skin property (Hypixel's custom icons). Only needed without an item stack. */
object HeadIcon {

    // null = failed, so bad values aren't retried every frame.
    private val urls = HashMap<String, String?>()
    private val images = HashMap<String, Image?>()

    fun draw(base64: String, x: Number, y: Number, size: Number) {
        val image = image(base64) ?: return
        // 64x64 skin: face at (8,8), hat at (40,8).
        Render.image(image, x, y, size, size, 8, 8, 8, 8, 64, 64)
        Render.image(image, x, y, size, size, 40, 8, 8, 8, 64, 64)
    }

    /** Null while downloading or on a bad value. */
    fun image(base64: String): Image? {
        val url = urls.getOrPut(base64) { skinUrl(base64) } ?: return null
        return images.getOrPut(url) { runCatching { Render.createImage(url) }.getOrNull() }
    }

    private fun skinUrl(base64: String): String? = runCatching {
        val json = Base64.getDecoder().decode(base64).decodeToString()
        JsonParser.parseString(json).asJsonObject
            .getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").asString
    }.getOrNull()
}
