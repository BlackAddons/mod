package org.blackaddons.platform.mixin.render.sodium;

import org.blackaddons.platform.impl.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * A view's async cull task can finish after the main camera turns back and still pass Sodium's off-by-one stability
 * check, which leaves the main frame on the view's frustum-tested LOCAL tree. Views read the frustum-agnostic trees
 * instead.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class SodiumEntityViewCullMixin {
    @Inject(method = "scheduleAsyncWork", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void blackaddons$skipEntityViewCull(CallbackInfo ci) {
        if (EntityView.getRendering()) ci.cancel();
    }
}
