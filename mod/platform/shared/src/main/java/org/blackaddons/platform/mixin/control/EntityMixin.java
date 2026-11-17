package org.blackaddons.platform.mixin.control;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.blackaddons.api.camera.CameraController;
import org.blackaddons.api.camera.CameraControllers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onTurn(double xo, double yo, CallbackInfo ci) {
        if (!((Object) this instanceof LocalPlayer)) {
            return;
        }
        CameraController controller = CameraControllers.active();
        if (controller != null) {
            controller.onTurn(xo, yo);
            ci.cancel();
        }
    }
}
