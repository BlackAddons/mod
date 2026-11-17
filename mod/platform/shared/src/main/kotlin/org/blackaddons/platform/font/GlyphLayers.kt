package org.blackaddons.platform.font

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.network.chat.Style
import org.blackaddons.platform.compat.GpuTextureView
import org.blackaddons.platform.compat.RenderPipeline
import org.joml.Matrix4f
import org.joml.Matrix4fc

internal class OrderedDepthGlyph(private val glyph: TextRenderable.Styled, private val depth: Float) : TextRenderable.Styled by glyph {
    override fun render(matrix: Matrix4fc, consumer: VertexConsumer, light: Int, glowing: Boolean) =
        glyph.render(DEPTH_TRANSFORM.set(matrix).translate(0f, 0f, depth), consumer, light, glowing)

    private companion object {
        val DEPTH_TRANSFORM = Matrix4f()
    }
}

internal class LayeredGlyph(
    private val shadow: TextRenderable.Styled?,
    private val outline: TextRenderable.Styled?,
    private val main: TextRenderable.Styled,
    private val world: Boolean,
) : TextRenderable.Styled {
    private val left = minOf(
        main.left(), shadow?.left() ?: Float.POSITIVE_INFINITY,
        outline?.left() ?: Float.POSITIVE_INFINITY,
    )
    private val top = minOf(
        main.top(), shadow?.top() ?: Float.POSITIVE_INFINITY,
        outline?.top() ?: Float.POSITIVE_INFINITY,
    )
    private val right = maxOf(
        main.right(), shadow?.right() ?: Float.NEGATIVE_INFINITY,
        outline?.right() ?: Float.NEGATIVE_INFINITY,
    )
    private val bottom = maxOf(
        main.bottom(), shadow?.bottom() ?: Float.NEGATIVE_INFINITY,
        outline?.bottom() ?: Float.NEGATIVE_INFINITY,
    )

    override fun render(matrix: Matrix4fc, consumer: VertexConsumer, light: Int, glowing: Boolean) {
        shadow?.render(matrix, consumer, light, glowing)
        outline?.let { glyph ->
            if (world) renderTranslated(glyph, matrix, consumer, light, glowing, depth = OUTLINE_DEPTH)
            else glyph.render(matrix, consumer, light, glowing)
        }
        if (world) renderTranslated(main, matrix, consumer, light, glowing, depth = MAIN_DEPTH)
        else main.render(matrix, consumer, light, glowing)
    }

    override fun renderType(mode: Font.DisplayMode): RenderType = main.renderType(mode)
    override fun textureView(): GpuTextureView = main.textureView()
    override fun guiPipeline(): RenderPipeline = main.guiPipeline()
    override fun style(): Style = main.style()
    override fun left(): Float = left
    override fun top(): Float = top
    override fun right(): Float = right
    override fun bottom(): Float = bottom

    private fun renderTranslated(
        glyph: TextRenderable.Styled,
        matrix: Matrix4fc,
        consumer: VertexConsumer,
        light: Int,
        glowing: Boolean,
        x: Float = 0f,
        y: Float = 0f,
        depth: Float,
    ) {
        glyph.render(TRANSFORM.set(matrix).translate(x, y, depth), consumer, light, glowing)
    }

    private companion object {
        const val OUTLINE_DEPTH = 0.015f
        const val MAIN_DEPTH = 0.03f
        val TRANSFORM = Matrix4f()
    }
}
