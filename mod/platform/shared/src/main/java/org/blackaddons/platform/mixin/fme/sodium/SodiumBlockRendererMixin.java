package org.blackaddons.platform.mixin.fme.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.blackaddons.platform.features.impl.visual.fme.FmeTextureRemapper;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {
    @Unique private int blackaddons$fmeTint = 0xFFFFFF;
    @Unique private BlockState blackaddons$fmeTextureState;
    @Unique private LevelSlice blackaddons$level;
    @Unique private BlockPos blackaddons$blockPos;
    @Unique private int blackaddons$textureTint = 0xFFFFFF;
    @Unique private final Vector2f blackaddons$uv = new Vector2f();
    @Unique private final Vector3f blackaddons$pos = new Vector3f();

    @Inject(method = "prepare", at = @At("HEAD"), require = 0, remap = false)
    private void blackaddons$captureLevel(
        ChunkBuildBuffers buffers,
        LevelSlice level,
        TranslucentGeometryCollector collector,
        CallbackInfo ci
    ) {
        this.blackaddons$level = level;
    }

    @Inject(method = "renderModel", at = @At("HEAD"), require = 0, remap = false)
    private void blackaddons$captureFmeTint(
        BlockStateModel model,
        BlockState state,
        BlockPos pos,
        BlockPos origin,
        CallbackInfo ci
    ) {
        this.blackaddons$fmeTint = FmeBlockRemapper.tintAt(pos.getX(), pos.getY(), pos.getZ());
        this.blackaddons$fmeTextureState = FmeBlockRemapper.textureStateAt(pos.getX(), pos.getY(), pos.getZ());
        this.blackaddons$blockPos = pos;
    }

    @Inject(method = "processQuad", at = @At("HEAD"), require = 0, remap = false)
    private void blackaddons$retextureFmeQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        this.blackaddons$textureTint = 0xFFFFFF;
        if (this.blackaddons$fmeTextureState != null) {
            var source = quad.cachedSprite();
            var face = FmeTextureRemapper.faceFor(this.blackaddons$fmeTextureState, quad.getNominalFace());
            if (source != null && source != face.getSprite()) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    var texUv = quad.copyUv(vertex, this.blackaddons$uv);
                    var pos = quad.copyPos(vertex, this.blackaddons$pos);
                    long uv = FmeTextureRemapper.mapUv(
                        texUv.x, texUv.y, source, face,
                        this.blackaddons$fmeTextureState, quad.getNominalFace(),
                        pos.x, pos.y, pos.z
                    );
                    quad.setUV(vertex, UVPair.unpackU(uv), UVPair.unpackV(uv));
                }
                quad.cachedSprite(face.getSprite());
            }
            if (face.getMaterial() != null) {
                quad.setRenderType(face.getMaterial().layer());
                quad.setItemRenderType(face.getMaterial().itemRenderType());
            }
            quad.setTintIndex(-1);
            if (this.blackaddons$level != null) {
                this.blackaddons$textureTint = FmeBlockRemapper.textureTint(
                    this.blackaddons$fmeTextureState, quad.getNominalFace(), this.blackaddons$level, this.blackaddons$blockPos
                );
            }
        }
    }

    @Inject(
        method = "processQuad",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;shadeQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;Lnet/caffeinemc/mods/sodium/client/model/light/LightMode;ZLnet/caffeinemc/mods/sodium/client/render/model/SodiumShadeMode;)V",
            shift = At.Shift.BEFORE
        ),
        require = 0,
        remap = false
    )
    private void blackaddons$applyFmeTint(MutableQuadViewImpl quad, CallbackInfo ci) {
        int tint = FmeBlockRemapper.multiplyTint(this.blackaddons$fmeTint, this.blackaddons$textureTint);
        if ((tint & 0xFFFFFF) == 0xFFFFFF) {
            return;
        }
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.setColor(vertex, FmeBlockRemapper.multiplyTint(quad.baseColor(vertex), tint));
        }
    }
}
