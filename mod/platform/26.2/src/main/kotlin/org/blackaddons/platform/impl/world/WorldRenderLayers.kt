package org.blackaddons.platform.impl.world

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import org.blackaddons.platform.util.modId

/**
 * Depth-tested and see-through render types for world geometry. Built once, because `RenderPipelines.register` rejects
 * duplicates. Vanilla has no POSITION_COLOR filled-triangle pipeline, hence [FILLED].
 */
internal object WorldRenderLayers {

    // ALWAYS_PASS without depth write draws through terrain. Declared first: object initializers run in source order.
    private val NO_DEPTH = DepthStencilState(CompareOp.ALWAYS_PASS, false)
    private val DEPTH_BIAS = DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1f, 1f)

    val LINES: RenderType = layer("ba_lines", RenderPipelines.LINES)
    val LINES_DEPTH_BIASED: RenderType = layer(
        "ba_lines_depth_biased",
        RenderPipelines.LINES_DEPTH_BIAS.also(IrisCompat::assignLinesIfLoaded),
    )
    val LINES_THROUGH: RenderType = layer("ba_lines_through", pipeline("lines_through_walls") {
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET).withDepthStencilState(NO_DEPTH)
    })
    val FILLED: RenderType = layer("ba_filled", pipeline("filled") { filledBuilder() })
    val FILLED_DEPTH_BIASED: RenderType = layer("ba_filled_depth_biased", pipeline("filled_depth_biased") {
        filledBuilder().withDepthStencilState(DEPTH_BIAS)
    })
    val FILLED_THROUGH: RenderType = layer("ba_filled_through", pipeline("filled_through_walls") {
        filledBuilder().withDepthStencilState(NO_DEPTH)
    })


    private fun filledBuilder(): RenderPipeline.Builder =
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withCull(true)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)

    private fun pipeline(name: String, build: () -> RenderPipeline.Builder): RenderPipeline =
        RenderPipelines.register(build().withLocation(modId("pipeline/$name")).build())

    private fun layer(name: String, pipeline: RenderPipeline): RenderType =
        RenderType.create(name, RenderSetup.builder(pipeline).createRenderSetup())
}
