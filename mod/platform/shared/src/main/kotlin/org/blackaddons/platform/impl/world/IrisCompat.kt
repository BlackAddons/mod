package org.blackaddons.platform.impl.world
import net.fabricmc.loader.api.FabricLoader
import net.irisshaders.iris.api.v0.IrisApi
import net.irisshaders.iris.api.v0.IrisProgram
import org.blackaddons.platform.compat.RenderPipeline

internal object IrisCompat {
    fun assignLinesIfLoaded(pipeline: RenderPipeline) {
        if (FabricLoader.getInstance().isModLoaded("iris")) Loaded.assignLines(pipeline)
    }

    fun shaderPackInUse(): Boolean = FabricLoader.getInstance().isModLoaded("iris") && Loaded.shaderPackInUse()

    private object Loaded {
        fun assignLines(pipeline: RenderPipeline) = IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.LINES)
        fun shaderPackInUse(): Boolean = IrisApi.getInstance().isShaderPackInUse
    }
}
