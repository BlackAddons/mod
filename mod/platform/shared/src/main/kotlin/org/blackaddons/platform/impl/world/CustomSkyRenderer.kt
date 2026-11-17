package org.blackaddons.platform.impl.world

import org.blackaddons.api.world.SkyStyle
import org.blackaddons.platform.compat.GpuBuffer
import org.blackaddons.platform.compat.GpuTextureView
import org.blackaddons.platform.compat.RenderPipeline
import java.util.EnumMap

/*
 * One full-screen triangle in place of the overworld sky, drawn over its style's bakes. Everything is lazy, because all
 * of it needs the GPU device.
 */
internal object CustomSkyRenderer {

    private val pipelines = EnumMap<SkyStyle, RenderPipeline>(SkyStyle::class.java)
    private val baked = HashMap<SkyBake, GpuTextureView>()
    private val ubo by lazy { newSkyUniforms(SkyReplacement.UBO_SIZE) }
    private var mainPassBuffer: GpuBuffer? = null

    @JvmStatic
    fun draw() {
        val style = SkyReplacement.style ?: return
        val viewPass = EntityView.rendering
        val buffer = if (viewPass) mainPassBuffer ?: return else ubo.currentBuffer()
        if (!viewPass && !writeSkyUniforms(buffer)) return
        val bakes = style.bakes.map { it to baked.getOrPut(it) { renderSkyBake(it) } }
        drawSkyPass(pipelines.getOrPut(style) { newSkyPipeline(style) }, buffer, bakes)
        if (viewPass) return
        mainPassBuffer = buffer
        ubo.rotate()
    }
}
