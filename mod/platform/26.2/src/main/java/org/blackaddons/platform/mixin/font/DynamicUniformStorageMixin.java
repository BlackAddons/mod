package org.blackaddons.platform.mixin.font;

import net.minecraft.client.renderer.DynamicUniformStorage;
import org.blackaddons.platform.compat.PreparedTextTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicUniformStorage.class)
public abstract class DynamicUniformStorageMixin {
    @Inject(method = {"endFrame", "resizeBuffers"}, at = @At("HEAD"))
    private void blackaddons$forgetPreparedText(CallbackInfo ci) {
        PreparedTextTypes.invalidate();
    }
}
