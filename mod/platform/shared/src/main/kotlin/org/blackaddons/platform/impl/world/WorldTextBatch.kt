package org.blackaddons.platform.impl.world
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.phys.Vec3
import org.blackaddons.core.render.WorldRenderPrefs
import org.blackaddons.core.render.WorldTextFont
import org.blackaddons.platform.compat.drawText
import org.blackaddons.platform.compat.mainCamera
import org.blackaddons.platform.font.MinecraftGlobalFonts
import org.blackaddons.platform.util.modId
import java.awt.Color

/** World labels, submitted after all geometry so they're never behind boxes or beams. */
internal class WorldTextBatch {

    private val pending = ArrayList<Entry>()
    private val lines = WorldTextFont.entries.associateWith { LineCache() }
    private var cachedFontRevision = Int.MIN_VALUE

    fun add(text: String, x: Double, y: Double, z: Double, color: Color, scale: Float, throughBlocks: Boolean) {
        pending += Entry(text, x, y, z, color, scale, throughBlocks)
    }

    fun flush(ctx: LevelRenderContext?) {
        if (pending.isEmpty()) return
        if (ctx == null) {
            pending.clear()
            return
        }
        val stack = ctx.poseStack()
        val mc = Minecraft.getInstance()
        val camera = mc.mainCamera
        val cp = camera.position()
        val look = Vec3.directionFromRotation(camera.xRot(), camera.yRot())
        val font = mc.font
        val worldFont = WorldRenderPrefs.worldFont()
        refreshLineCache()
        for (t in pending) {
            val dx = t.x - cp.x
            val dy = t.y - cp.y
            val dz = t.z - cp.z
            if (dx * look.x + dy * look.y + dz * look.z < 0.0) continue
            val s = t.scale * TEXT_SCALE
            val displayMode = if (t.throughBlocks) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
            val line = line(t.text, worldFont, font)
            // Billboard: move to the camera-relative point, face the camera, apply the text scale.
            stack.pushPose()
            stack.translate(dx.toFloat(), dy.toFloat(), dz.toFloat())
            stack.last().rotate(camera.rotation())
            stack.scale(s, -s, s)
            // No background or outline, full-bright.
            ctx.drawText(
                stack, font, line.text, -line.width / 2f,
                displayMode, FULL_BRIGHT, t.color.rgb,
            )
            stack.popPose()
        }
        pending.clear()
    }

    private fun refreshLineCache() {
        val revision = MinecraftGlobalFonts.fontRevision()
        if (revision == cachedFontRevision) return
        lines.values.forEach(LineCache::clear)
        cachedFontRevision = revision
    }

    private fun line(text: String, worldFont: WorldTextFont, font: Font): CachedLine {
        val cache = lines.getValue(worldFont)
        cache[text]?.let { return it }
        val styled = Component.literal(text).also {
            val fontId = when (worldFont) {
                WorldTextFont.VANILLA -> VANILLA_WORLD
                WorldTextFont.CUSTOM -> MinecraftGlobalFonts.WORLD_FONT
            }
            it.style = Style.EMPTY.withFont(FontDescription.Resource(fontId))
        }
        return CachedLine(styled.visualOrderText, font.width(styled)).also { cache[text] = it }
    }

    private class Entry(
        val text: String,
        val x: Double, val y: Double, val z: Double,
        val color: Color, val scale: Float, val throughBlocks: Boolean,
    )

    private data class CachedLine(val text: FormattedCharSequence, val width: Int)

    private class LineCache : LinkedHashMap<String, CachedLine>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLine>): Boolean =
            size > MAX_CACHED_LINES
    }

    private companion object {
        const val FULL_BRIGHT = 0xF000F0
        const val MAX_CACHED_LINES = 256

        /** Scale 1 reads like a nameplate. */
        const val TEXT_SCALE = 0.025f

        /**
         * Own font id so a replaced minecraft:default doesn't affect vanilla labels. Not via WorldRenderLayers,
         * touching it registers pipelines.
         */
        val VANILLA_WORLD: Identifier = modId("vanilla_world")
    }
}
