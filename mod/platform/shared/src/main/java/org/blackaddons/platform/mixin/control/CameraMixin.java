package org.blackaddons.platform.mixin.control;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.blackaddons.api.camera.CameraController;
import org.blackaddons.api.camera.CameraControllers;
import org.blackaddons.platform.impl.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow private Entity entity;
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void move(float forwards, float up, float right);
    @Shadow protected abstract float getMaxZoom(float cameraDist);
    @Shadow public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void blackaddons$onUpdateAfterAlign(DeltaTracker deltaTracker, CallbackInfo ci) {
        Entity entity = this.entity;
        if (entity == null) {
            return;
        }
        EntityView.View view = EntityView.getCurrent();
        if (view != null) {
            Vec3 eye = entity.getEyePosition(getCameraEntityPartialTicks(deltaTracker));
            this.detached = false;
            setRotation(view.getYaw(), view.getPitch());
            setPosition(eye.x, eye.y, eye.z);
            return;
        }
        CameraController controller = CameraControllers.active();
        if (controller == null) {
            return;
        }

        float tickDelta = getCameraEntityPartialTicks(deltaTracker);
        this.detached = true;
        setRotation(controller.rotYaw(tickDelta), controller.rotPitch(tickDelta));

        float pullback = controller.getPullback();
        if (pullback > 0.0F) {
            // Third person: anchor at the eye, then pull back without collision (see getMaxZoom).
            Vec3 eye = entity.getEyePosition(tickDelta);
            setPosition(eye.x, eye.y, eye.z);
            move(-getMaxZoom(pullback), 0.0F, 0.0F);
        } else {
            setPosition(controller.posX(tickDelta), controller.posY(tickDelta), controller.posZ(tickDelta));
        }
    }

    @Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onGetMaxZoom(float cameraDist, CallbackInfoReturnable<Float> cir) {
        if (CameraControllers.active() != null) {
            cir.setReturnValue(cameraDist);
        }
    }
}
