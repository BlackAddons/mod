package org.blackaddons.platform.mixin.compat.xeno;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.blackaddons.platform.impl.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;

@Pseudo
@Mixin(targets = "com.xeno.client.common.render.culling.XenoOcclusionGraph", remap = false)
public abstract class XenoOcclusionGraphMixin {
    @WrapOperation(
        method = "addSectionsInFrustum",
        at = @At(
            value = "INVOKE",
            target = "Lcom/xeno/client/common/render/culling/XenoOcclusionCuller;collectVisibleSections(Lnet/minecraft/client/renderer/culling/Frustum;ZIIIIIILjava/util/List;Ljava/util/List;)V"
        ),
        require = 0,
        remap = false
    )
    private void blackaddons$useSyncFrustumForEntityView(
        @Coerce Object culler,
        Frustum frustum,
        boolean forceSyncFallback,
        int camSecX,
        int camSecY,
        int camSecZ,
        int camBlockX,
        int camBlockY,
        int camBlockZ,
        List<SectionRenderDispatcher.RenderSection> outVisible,
        List<SectionRenderDispatcher.RenderSection> outNearby,
        Operation<Void> original
    ) {
        if (EntityView.getRendering()) {
            EntityView.View view = EntityView.getCurrent();
            if (view != null) {
                BlockPos camPos = view.getEntity().blockPosition();
                original.call(
                    culler,
                    frustum,
                    true,
                    SectionPos.blockToSectionCoord(camPos.getX()),
                    SectionPos.blockToSectionCoord(camPos.getY()),
                    SectionPos.blockToSectionCoord(camPos.getZ()),
                    camPos.getX(),
                    camPos.getY(),
                    camPos.getZ(),
                    outVisible,
                    outNearby
                );
                return;
            }
            original.call(culler, frustum, true, camSecX, camSecY, camSecZ, camBlockX, camBlockY, camBlockZ, outVisible, outNearby);
            return;
        }
        original.call(culler, frustum, forceSyncFallback, camSecX, camSecY, camSecZ, camBlockX, camBlockY, camBlockZ, outVisible, outNearby);
    }
}
