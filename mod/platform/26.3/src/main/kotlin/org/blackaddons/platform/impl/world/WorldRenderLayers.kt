package org.blackaddons.platform.impl.world

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.oit.OitPipelineSet
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import org.blackaddons.platform.Blackaddons
import org.blackaddons.platform.util.modId
import java.util.function.Consumer

/**
 * Depth-tested and see-through render types for world geometry. Built once, because `RenderPipelines.register` rejects
 * duplicates. Vanilla has no POSITION_COLOR filled-triangle pipeline, hence [FILLED]. Every layer blends, so Improved
 * Transparency routes it through OIT, which throws without OIT pipelines.
 */
internal object WorldRenderLayers {

    // ALWAYS_PASS without depth write draws through terrain. Declared first: object initializers run in source order.
    private val NO_DEPTH = DepthStencilState(CompareOp.ALWAYS_PASS, false)
    private val DEPTH_BIAS = DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1f, 1f)
    private val LINES_DEPTH_BIAS = DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 1f, 1f)

    val LINES: RenderType = layer("ba_lines", RenderPipelines.LINES_TRANSLUCENT, RenderPipelines.OIT_LINES_TRANSLUCENT)
    val LINES_DEPTH_BIASED: RenderType = layer(
        "ba_lines_depth_biased",
        pipeline("lines_depth_biased") { linesBuilder().withDepthStencilState(LINES_DEPTH_BIAS) }
            .also(IrisCompat::assignLinesIfLoaded),
        oit("lines_depth_biased", oitLinesBuilder(), DEPTH_BIAS),
    )
    val LINES_THROUGH: RenderType = layer(
        "ba_lines_through",
        pipeline("lines_through_walls") { linesBuilder().withDepthStencilState(NO_DEPTH) },
        oit("lines_through_walls", oitLinesBuilder(), NO_DEPTH),
    )
    val FILLED: RenderType = layer("ba_filled", pipeline("filled") { filledBuilder() }, oit("filled", oitFilledBuilder()))
    val FILLED_DEPTH_BIASED: RenderType = layer(
        "ba_filled_depth_biased",
        pipeline("filled_depth_biased") { filledBuilder().withDepthStencilState(DEPTH_BIAS) },
        oit("filled_depth_biased", oitFilledBuilder(), DEPTH_BIAS),
    )
    val FILLED_THROUGH: RenderType = layer(
        "ba_filled_through",
        pipeline("filled_through_walls") { filledBuilder().withDepthStencilState(NO_DEPTH) },
        oit("filled_through_walls", oitFilledBuilder(), NO_DEPTH),
    )


    private fun linesBuilder(): RenderPipeline.Builder =
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))

    private fun filledBuilder(): RenderPipeline.Builder =
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withCull(true)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)

    private fun oitLinesBuilder(): RenderPipeline.Builder = RenderPipeline.builder(RenderPipelines.OIT_LINES_SNIPPET)

    private fun oitFilledBuilder(): RenderPipeline.Builder =
        RenderPipeline.builder(RenderPipelines.OIT_DEBUG_FILLED_SNIPPET)
            .withCull(true)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)

    private fun pipeline(name: String, build: () -> RenderPipeline.Builder): RenderPipeline =
        RenderPipelines.register(build().withLocation(modId("pipeline/$name")).build())

    private fun oit(name: String, base: RenderPipeline.Builder, depth: DepthStencilState? = null): OitPipelineSet {
        val builder = OitPipelineSet.builder("${Blackaddons.MOD_ID}_$name", base)
        if (depth != null) {
            val withDepth = Consumer<RenderPipeline.Builder> { it.withDepthStencilState(depth) }
            builder.withDepthBoundsModifier(withDepth).withTransmittanceModifier(withDepth).withAccumulateModifier(withDepth)
        }
        return RenderPipelines.register(builder.build())
    }

    private fun layer(name: String, pipeline: RenderPipeline, oit: OitPipelineSet): RenderType =
        RenderType.create(name, RenderSetup.builder(pipeline).setOitPipelines(oit).createRenderSetup())
}
