package org.blackaddons.platform.mixin.fme.vanilla;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.blackaddons.platform.features.impl.visual.fme.FmeTextureRemapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @Shadow @Final private QuadInstance quadInstance;
    @Unique private int blackaddons$fmeTint = 0xFFFFFF;
    @Unique private BlockState blackaddons$fmeTextureState;

    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void blackaddons$captureFmeTint(
        BlockQuadOutput output,
        float x,
        float y,
        float z,
        BlockAndTintGetter level,
        BlockPos pos,
        BlockState state,
        BlockStateModel model,
        long seed,
        CallbackInfo ci
    ) {
        this.blackaddons$fmeTint = FmeBlockRemapper.tintAt(pos.getX(), pos.getY(), pos.getZ());
        this.blackaddons$fmeTextureState = FmeBlockRemapper.textureStateAt(pos.getX(), pos.getY(), pos.getZ());
    }

    @Inject(
        method = "shouldRenderFace",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"),
        cancellable = true
    )
    private void blackaddons$keepFaceBehindFmeSeeThrough(
        BlockAndTintGetter level,
        BlockState state,
        Direction direction,
        BlockPos neighborPos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (FmeBlockRemapper.seeThroughFace(state, neighborPos, direction.getOpposite())) {
            cir.setReturnValue(true);
        }
    }

    @ModifyVariable(
        method = "putQuadWithTint",
        at = @At("HEAD"),
        argsOnly = true,
        index = 8
    )
    private BakedQuad blackaddons$retextureFmeQuad(BakedQuad quad) {
        return this.blackaddons$fmeTextureState == null ? quad : FmeTextureRemapper.retexture(quad, this.blackaddons$fmeTextureState);
    }

    @Inject(method = "putQuadWithTint", at = @At("HEAD"))
    private void blackaddons$applyFmeTint(
        BlockQuadOutput output,
        float x,
        float y,
        float z,
        BlockAndTintGetter level,
        BlockState state,
        BlockPos pos,
        BakedQuad quad,
        CallbackInfo ci
    ) {
        int tint = this.blackaddons$fmeTint;
        if (this.blackaddons$fmeTextureState != null) {
            tint = FmeBlockRemapper.multiplyTint(
                tint, FmeBlockRemapper.textureTint(this.blackaddons$fmeTextureState, quad.direction(), level, pos)
            );
        }
        if ((tint & 0xFFFFFF) == 0xFFFFFF) {
            return;
        }
        for (int vertex = 0; vertex < 4; vertex++) {
            this.quadInstance.setColor(vertex, FmeBlockRemapper.multiplyTint(this.quadInstance.getColor(vertex), tint));
        }
    }
}
