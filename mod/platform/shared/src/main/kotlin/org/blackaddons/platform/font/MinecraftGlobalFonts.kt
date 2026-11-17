package org.blackaddons.platform.font

import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.font.GlyphRenderTypes
import net.minecraft.resources.Identifier
import org.blackaddons.core.font.FontSlot
import org.blackaddons.core.font.GlobalFontBridge
import org.blackaddons.platform.compat.GpuTexture
import org.blackaddons.platform.compat.clearFontPage
import org.blackaddons.platform.compat.emitOrderPolygonOffsetText
import org.blackaddons.platform.compat.emitOrderSeeThroughText
import org.blackaddons.platform.util.modId

object MinecraftGlobalFonts {
    val WORLD_FONT: Identifier = modId("world")

    private val minecraft = NativeFontSlot(Minecraft.DEFAULT_FONT)
    private val world = NativeFontSlot(WORLD_FONT)
    private val slots = listOf(minecraft, world)

    fun install() {
        GlobalFontBridge.apply = { slot, request, done -> slot(slot).apply(request, done) }
        GlobalFontBridge.restore = { slot, done -> slot(slot).restore(done) }
        ClientTickEvents.END_CLIENT_TICK.register { slots.forEach(NativeFontSlot::tick) }
    }

    private fun slot(slot: FontSlot) = when (slot) {
        FontSlot.MINECRAFT -> minecraft
        FontSlot.WORLD -> world
    }

    @JvmStatic
    fun useLinearSampling(textureName: String): Boolean = slots.any { it.owns(textureName) }

    @JvmStatic
    var stitchingSmoothGlyph = false

    @JvmStatic
    fun isEnabled(): Boolean = minecraft.enabled || world.enabled

    @JvmStatic
    fun isMinecraftEnabled(): Boolean = minecraft.enabled

    @JvmStatic
    fun atlasSize(textureName: String, smooth: Boolean): Int =
        if (smooth) slots.firstOrNull { it.owns(textureName) }?.atlasSize ?: VANILLA_ATLAS_SIZE else VANILLA_ATLAS_SIZE

    @JvmStatic
    fun fontRevision(): Int = slots.sumOf { it.fontRevision }

    fun worldBucketKey(pixelsPerUnit: Float): Int =
        minecraft.worldBucket(pixelsPerUnit) * BUCKET_KEY_STRIDE + world.worldBucket(pixelsPerUnit)

    @JvmStatic
    fun recordAtlasPage(textureName: String, colored: Boolean) {
        slots.firstOrNull { it.owns(textureName) }?.recordAtlasPage(textureName, colored)
    }

    @JvmStatic
    fun inEmitOrder(texture: String, renderTypes: GlyphRenderTypes, colored: Boolean): GlyphRenderTypes {
        val id = Identifier.parse(texture)
        return GlyphRenderTypes(
            renderTypes.normal(), emitOrderSeeThroughText(id, colored, renderTypes.seeThrough()),
            emitOrderPolygonOffsetText(id, colored), renderTypes.guiPipeline(),
        )
    }

    @JvmStatic
    fun clearAtlasPage(texture: GpuTexture) = clearFontPage(RenderSystem.getDevice().createCommandEncoder(), texture)

    private const val BUCKET_KEY_STRIDE = 64
}
