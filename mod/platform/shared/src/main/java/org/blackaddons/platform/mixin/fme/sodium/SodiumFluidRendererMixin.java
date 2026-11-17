package org.blackaddons.platform.mixin.fme.sodium;

import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class SodiumFluidRendererMixin {
    @Shadow @Final private int[] quadColors;
    @Unique private final ThreadLocal<Integer> blackaddons$fmeTint = ThreadLocal.withInitial(() -> 0xFFFFFF);

    @Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
    private void blackaddons$captureFmeTint(
        LevelSlice level,
        BlockState state,
        FluidState fluidState,
        BlockPos pos,
        BlockPos origin,
        TranslucentGeometryCollector collector,
        ChunkModelBuilder builder,
        Material material,
        ColorProvider<FluidState> colorProvider,
        FluidModel model,
        CallbackInfo ci
    ) {
        this.blackaddons$fmeTint.set(FmeBlockRemapper.tintAt(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Inject(
        method = "updateQuad",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/model/color/ColorProvider;getColors(Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos$MutableBlockPos;Ljava/lang/Object;Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;[IZ)V",
            shift = At.Shift.AFTER
        ),
        require = 0,
        remap = false
    )
    private void blackaddons$applyFmeTint(
        ModelQuadViewMutable quad,
        LevelSlice level,
        BlockPos pos,
        LightPipeline lighter,
        Direction direction,
        ModelQuadFacing facing,
        float brightness,
        ColorProvider<FluidState> colorProvider,
        FluidState fluidState,
        CallbackInfo ci
    ) {
        int tint = this.blackaddons$fmeTint.get();
        if (tint == 0xFFFFFF) {
            return;
        }
        for (int vertex = 0; vertex < this.quadColors.length; vertex++) {
            this.quadColors[vertex] = FmeBlockRemapper.multiplyTint(this.quadColors[vertex], tint);
        }
    }
}
