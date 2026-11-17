package org.blackaddons.platform.impl.world

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.UniformType
import com.mojang.renderpearl.api.textures.AddressMode
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.RenderPipelines
import org.blackaddons.api.world.SkyStyle
import org.blackaddons.platform.util.modId
import org.joml.Vector4f
import java.util.Optional
import java.util.OptionalDouble

internal fun newSkyUniforms(size: Int) =
    MappableRingBuffer({ "BlackAddons custom sky" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_MAP_WRITE, size)

internal fun writeSkyUniforms(buffer: GpuBuffer): Boolean = buffer.map(false, true).use { SkyReplacement.write(it.data()) }

internal fun newSkyPipeline(style: SkyStyle): RenderPipeline {
    val layout = BindGroupLayout.builder().withUniform("CustomSky", UniformType.UNIFORM_BUFFER)
    style.bakes.indices.forEach { layout.withUniform(SKY_BAKE_SAMPLERS[it], UniformType.COMBINED_IMAGE_SAMPLER) }
    return RenderPipelines.register(
        RenderPipeline.builder()
            .withLocation(modId("pipeline/${style.shader}"))
            .withVertexShader(modId("core/custom_sky"))
            .withFragmentShader(modId("core/custom_sky"))
            .withShaderDefine(style.define)
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(layout.build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build(),
    )
}

internal fun renderSkyBake(bake: SkyBake): GpuTextureView {
    val device = RenderSystem.getDevice()
    val pipeline = RenderPipelines.register(
        RenderPipeline.builder()
            .withLocation(modId("pipeline/${bake.shader}"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(modId("core/sky_bake"))
            .withShaderDefine(bake.define)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build(),
    )
    val texture = device.createTexture(
        "BlackAddons ${bake.shader}", GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT,
        GpuFormat.RGBA8_UNORM, bake.width, bake.height, 1, 1,
    )
    return device.createTextureView(texture).also { view ->
        device.createCommandEncoder().createRenderPass({ "BlackAddons ${bake.shader}" }, view, Optional.empty()).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
            pass.draw(3, 1, 0, 0)
        }
    }
}

internal fun drawSkyPass(pipeline: RenderPipeline, uniforms: GpuBuffer, bakes: List<Pair<SkyBake, GpuTextureView>>) {
    val transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), Vector4f(1f))
    val target = Minecraft.getInstance().gameRenderer.mainRenderTarget()
    RenderSystem.getDevice().createCommandEncoder().createRenderPass(
        { "BlackAddons custom sky" }, checkNotNull(target.colorTextureView), Optional.empty(),
        target.depthTextureView, OptionalDouble.empty(),
    ).use { pass ->
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
        RenderSystem.bindDefaultUniforms(pass)
        pass.setUniform("DynamicTransforms", transforms)
        pass.setUniform("CustomSky", uniforms)
        bakes.forEachIndexed { i, (bake, view) -> pass.setUniform(SKY_BAKE_SAMPLERS[i], view, sampler(bake)) }
        pass.draw(3, 1, 0, 0)
    }
}

/* Clamped vertically. Horizontally, a bake either wraps around (azimuth) or ends (half sphere, disc). */
private fun sampler(bake: SkyBake) = RenderSystem.getSamplerCache().getSampler(
    if (bake.wrapsAround) AddressMode.REPEAT else AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
    FilterMode.LINEAR, FilterMode.LINEAR, false,
)
