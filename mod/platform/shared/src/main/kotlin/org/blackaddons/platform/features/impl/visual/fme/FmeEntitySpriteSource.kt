package org.blackaddons.platform.features.impl.visual.fme

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.serialization.MapCodec
import net.minecraft.client.renderer.texture.SpriteContents
import net.minecraft.client.renderer.texture.atlas.SpriteSource
import net.minecraft.client.resources.metadata.animation.FrameSize
import net.minecraft.core.Direction
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import org.blackaddons.core.features.impl.visual.fme.FmeEntitySkinLayout
import org.blackaddons.core.log.Log
import org.blackaddons.platform.util.modId
import java.io.IOException

/** Adds closed block-entity skins as face sprites to the block atlas without overriding its definition. */
object FmeEntitySpriteSource : SpriteSource {
    private val CODEC: MapCodec<FmeEntitySpriteSource> = MapCodec.unit(this)
    private val CHEST_SKINS = setOf(
        "normal", "trapped", "ender", "christmas", "copper", "copper_exposed", "copper_weathered", "copper_oxidized",
    )

    /**
     * Chest faces are 14 texels, but a side that isn't a multiple of 16 caps the whole atlas's mip level. So it's
     * centered on 16 with edge texels repeated into the apron, and faces map to the inner area.
     */
    const val CHEST_INSET = 1f / 16f

    fun spriteId(kind: String, skin: Identifier, face: Direction): Identifier =
        modId("fme/" + kind + "/" + skin.namespace + "/" + skin.path + "/" + face.getName())

    override fun run(resources: ResourceManager, output: SpriteSource.Output) {
        addSkins(resources, output, "shulker")
        addSkins(resources, output, "chest")
    }

    override fun codec(): MapCodec<out SpriteSource> = CODEC

    private fun addSkins(resources: ResourceManager, output: SpriteSource.Output, kind: String) {
        val converter = FileToIdConverter("textures/entity/$kind", ".png")
        for ((file, resource) in converter.listMatchingResources(resources)) {
            val skin = converter.fileToId(file)
            if (skin.namespace != "minecraft") continue
            if (kind == "shulker" && !skin.path.startsWith("shulker")) continue
            if (kind == "chest" && skin.path !in CHEST_SKINS) continue
            for (face in Direction.entries) {
                val sprite = spriteId(kind, skin, face)
                output.add(sprite) { loadFace(sprite, resource, kind, face) }
            }
        }
    }

    private fun loadFace(sprite: Identifier, resource: Resource, kind: String, face: Direction): SpriteContents? {
        try {
            return resource.open().use { input ->
                NativeImage.read(input).use { skin ->
                    if (skin.width != skin.height || skin.width < 64 || skin.width % 64 != 0) {
                        Log.main.warn("FME skin {} must be a square multiple of 64 pixels", sprite)
                        return null
                    }
                    val scale = skin.width / 64
                    val side = 16 * scale
                    val pad = if (kind == "shulker") 0 else Math.round(CHEST_INSET * side)
                    val projected = NativeImage(side, side, false)
                    for (blit in FmeEntitySkinLayout.blits(kind, face.name)) {
                        copy(skin, projected, blit.sourceX, blit.sourceY, blit.targetX, blit.targetY, blit.width, blit.height, blit.flipY, scale, pad)
                    }
                    if (pad > 0) extendEdges(projected, pad)
                    SpriteContents(sprite, FrameSize(side, side), projected)
                }
            }
        } catch (exception: IOException) {
            Log.main.warn("Could not project FME skin {}", sprite, exception)
            return null
        } catch (exception: RuntimeException) {
            Log.main.warn("Could not project FME skin {}", sprite, exception)
            return null
        }
    }

    private fun copy(source: NativeImage, target: NativeImage, fromX: Int, fromY: Int, toX: Int, toY: Int, width: Int, height: Int, flipY: Boolean, scale: Int, pad: Int) {
        for (y in 0 until height * scale) {
            for (x in 0 until width * scale) {
                val sourceY = fromY * scale + if (flipY) height * scale - 1 - y else y
                val targetX = pad + toX * scale + x
                val targetY = pad + toY * scale + y
                val sourcePixel = source.getPixel(fromX * scale + x, sourceY)
                target.setPixel(targetX, targetY, over(sourcePixel, target.getPixel(targetX, targetY)))
            }
        }
    }

    private fun extendEdges(image: NativeImage, pad: Int) {
        val last = image.width - pad - 1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (x in pad..last && y in pad..last) continue
                image.setPixel(x, y, image.getPixel(x.coerceIn(pad, last), y.coerceIn(pad, last)))
            }
        }
    }

    private fun over(source: Int, target: Int): Int {
        val sourceAlpha = source ushr 24
        if (sourceAlpha == 0) return target
        if (sourceAlpha == 255) return source

        val targetAlpha = target ushr 24
        val inverseSourceAlpha = 255 - sourceAlpha
        val outputAlpha = sourceAlpha + (targetAlpha * inverseSourceAlpha + 127) / 255
        var output = outputAlpha shl 24
        for (shift in 0..16 step 8) {
            val sourceChannel = (source ushr shift) and 0xFF
            val targetChannel = (target ushr shift) and 0xFF
            val premultiplied = sourceChannel * sourceAlpha +
                (targetChannel * targetAlpha * inverseSourceAlpha + 127) / 255
            output = output or (((premultiplied + outputAlpha / 2) / outputAlpha) shl shift)
        }
        return output
    }
}
