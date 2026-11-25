package org.blackaddons.platform.ui.skin

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.blackaddons.api.ui.theme.GlassDecor
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.core.log.Log
import org.blackaddons.platform.util.modId

/**
 * [GlassDecor] marks of vanilla textures as runtime textures, read through the resource manager so resource packs
 * apply. Made once per texture region and theme. Render thread only.
 *
 * shortcut: a resource pack switch keeps the old marks until the theme changes or the game restarts; hook a reload
 * listener if that comes up.
 */
internal object Decor {

    /** The texture held only panel and frames: draw nothing. */
    val EMPTY: Identifier = modId("decor/empty")

    private data class Key(val texture: Identifier, val u: Int, val v: Int, val w: Int, val h: Int, val holes: Int, val shape: Boolean)

    private val made = HashMap<Key, Identifier?>()
    private var madeFor = 0L
    private var nextId = 0

    /**
     * The marks of a blit's texture region, logical units as passed to the blit. [holes] are slot centers inside the
     * region. Null when the texture can't be read, so the caller keeps vanilla's.
     */
    fun region(
        texture: Identifier, u: Int, v: Int, w: Int, h: Int, texW: Int, texH: Int, holes: IntArray, shape: Boolean = false,
    ): Identifier? {
        val theme = (Theme.BACKGROUND.rgb.toLong() shl 32) or (Theme.BORDER.rgb.toLong() and 0xFFFFFFFFL)
        if (theme != madeFor) {
            clear()
            madeFor = theme
        }
        val key = Key(texture, u, v, w, h, holes.contentHashCode(), shape)
        if (key in made) return made[key]
        return make(texture, u, v, w, h, texW, texH, holes, shape).also { made[key] = it }
    }

    /** A GUI sprite drawn at its own size, for icon buttons whose frame becomes glass. */
    fun sprite(sprite: Identifier, w: Int, h: Int): Identifier? =
        region(sprite.withPath { "textures/gui/sprites/$it.png" }, 0, 0, w, h, w, h, NO_HOLES)

    /** A GUI sprite's shape in white, to tint with a theme color (page arrows). */
    fun silhouette(sprite: Identifier, w: Int, h: Int): Identifier? =
        region(sprite.withPath { "textures/gui/sprites/$it.png" }, 0, 0, w, h, w, h, NO_HOLES, shape = true)

    /** One pixel of a GUI sprite at its own scale, or null when it can't be read. */
    fun color(sprite: Identifier, x: Int, y: Int): Int? {
        val texture = sprite.withPath { "textures/gui/sprites/$it.png" }
        return try {
            Minecraft.getInstance().resourceManager.getResource(texture).orElse(null)?.open()?.use(NativeImage::read)?.use {
                it.getPixel(x.coerceIn(0, it.width - 1), y.coerceIn(0, it.height - 1))
            }
        } catch (e: Exception) {
            Log.main.warn("Custom UI can't read $texture, keeping the vanilla sprite", e)
            null
        }
    }

    private fun make(
        texture: Identifier, u: Int, v: Int, w: Int, h: Int, texW: Int, texH: Int, holes: IntArray, shape: Boolean,
    ): Identifier? {
        val mc = Minecraft.getInstance()
        val image = try {
            mc.resourceManager.getResource(texture).orElse(null)?.open()?.use(NativeImage::read) ?: return null
        } catch (e: Exception) {
            Log.main.warn("Custom UI can't read $texture, keeping the vanilla texture", e)
            return null
        }
        image.use {
            val sx = it.width / texW.toFloat()
            val sy = it.height / texH.toFloat()
            val x0 = (u * sx).toInt()
            val y0 = (v * sy).toInt()
            val rw = minOf((w * sx).toInt(), it.width - x0)
            val rh = minOf((h * sy).toInt(), it.height - y0)
            if (rw <= 0 || rh <= 0) return null
            val px = IntArray(rw * rh) { i -> it.getPixel(x0 + i % rw, y0 + i / rw) }
            val scaled = IntArray(holes.size) { i -> (holes[i] * if (i % 2 == 0) sx else sy).toInt() }
            val marks = if (shape) IntArray(px.size) { i -> px[i] or WHITE }
            else GlassDecor.lift(px, rw, rh, scaled, Theme.BACKGROUND.rgb, Theme.BORDER.rgb) ?: return EMPTY
            val out = NativeImage(rw, rh, false)
            for (i in marks.indices) out.setPixel(i % rw, i / rw, marks[i])
            val id = modId("decor/${nextId++}")
            mc.textureManager.register(id, DynamicTexture({ "blackaddons-decor" }, out))
            return id
        }
    }

    private fun clear() {
        val textures = Minecraft.getInstance().textureManager
        for (id in made.values) if (id != null && id !== EMPTY) textures.release(id)
        made.clear()
    }

    private val NO_HOLES = IntArray(0)
    private const val WHITE = 0xFFFFFF
}
