package org.blackaddons.platform.compat

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.ScissorState
import net.minecraft.client.renderer.rendertype.PreparedRenderType
import net.minecraft.client.renderer.rendertype.RenderType
import org.joml.Matrix4f
import java.util.IdentityHashMap

object PreparedTextTypes {
    @JvmStatic
    var active = false

    private var epoch = 0
    private var cachedEpoch = -1
    private val modelView = Matrix4f()
    private val scissor = ScissorState()
    private val prepared = IdentityHashMap<RenderType, PreparedRenderType>()

    @JvmStatic
    fun invalidate() {
        epoch++
    }

    @JvmStatic
    fun cached(renderType: RenderType): PreparedRenderType? {
        val stack = RenderSystem.getModelViewStack()
        val currentScissor = RenderSystem.getScissorStateForRenderTypeDraws()
        if (cachedEpoch != epoch || !modelView.equals(stack, 0f) || !scissor.matches(currentScissor)) {
            prepared.clear()
            cachedEpoch = epoch
            modelView.set(stack)
            scissor.setFrom(currentScissor)
            return null
        }
        return prepared[renderType]
    }

    @JvmStatic
    fun store(renderType: RenderType, preparedType: PreparedRenderType) {
        prepared[renderType] = preparedType
    }

    private fun ScissorState.matches(other: ScissorState) = enabled() == other.enabled() &&
        (!enabled() || x() == other.x() && y() == other.y() && width() == other.width() && height() == other.height())
}
