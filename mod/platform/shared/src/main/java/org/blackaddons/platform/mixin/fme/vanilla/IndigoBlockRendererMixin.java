package org.blackaddons.platform.mixin.fme.vanilla;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricTextureAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.level.block.state.BlockState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.blackaddons.platform.features.impl.visual.fme.FmeTextureRemapper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public abstract class IndigoBlockRendererMixin {
    @Shadow(remap = false) @Final private BlockPos.MutableBlockPos scratchPos;
    @Shadow(remap = false) private BlockState blockState;
    @Unique private int blackaddons$fmeTint = 0xFFFFFF;
    @Unique private BlockState blackaddons$fmeTextureState;

    @Inject(method = "tesselateBlock", at = @At("HEAD"), remap = false)
    private void blackaddons$captureFmeTint(
            QuadEmitter output,
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
            method = "shouldCullFace",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"),
            cancellable = true,
            remap = false
    )
    private void blackaddons$keepFaceBehindFmeSeeThrough(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (FmeBlockRemapper.seeThroughFace(this.blockState, this.scratchPos, direction.getOpposite())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "transform",
            at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AltModelBlockRendererImpl;tintQuad(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MutableQuadView;)V"),
            remap = false
    )
    private void blackaddons$retextureFmeQuad(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (this.blackaddons$fmeTextureState != null) {
            /*
             * Atlas manager, not TextureManager: this is a chunk-compile worker, and getTexture loads missing textures
             * on the render system.
             */
            var atlas = (FabricTextureAtlas) Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            var source = atlas.spriteFinder().find(quad);
            var face = FmeTextureRemapper.faceFor(this.blackaddons$fmeTextureState, quad.nominalFace());
            if (source != null && source != face.getSprite()) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    long uv = FmeTextureRemapper.mapUv(
                            quad.u(vertex), quad.v(vertex), source, face,
                            this.blackaddons$fmeTextureState, quad.nominalFace(),
                            quad.x(vertex), quad.y(vertex), quad.z(vertex)
                    );
                    quad.uv(vertex, UVPair.unpackU(uv), UVPair.unpackV(uv));
                }
            }
            if (face.getMaterial() != null) {
                quad.chunkLayer(face.getMaterial().layer());
                quad.itemRenderType(face.getMaterial().itemRenderType());
            }
            quad.tintIndex(-1);
        }
    }

    @Inject(
            method = "transform",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AltModelBlockRendererImpl;tintQuad(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MutableQuadView;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void blackaddons$applyFmeTint(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (this.blackaddons$fmeTint != 0xFFFFFF) {
            quad.multiplyColor(0xFF000000 | this.blackaddons$fmeTint);
        }
    }
}
