package org.blackaddons.platform.mixin.control;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.blackaddons.platform.impl.player.MinecraftMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Unique
    private float blackaddons$aiStepRealYaw;

    @Unique
    private float blackaddons$aiStepRealPitch;

    @Unique
    private boolean blackaddons$aiStepSilent;

    @Unique
    private float blackaddons$sendPositionRealYaw;

    @Unique
    private float blackaddons$sendPositionRealPitch;

    @Unique
    private boolean blackaddons$sendPositionSilent;

    @Inject(method = "startUsingItem", at = @At("TAIL"))
    private void blackaddons$afterStartUsingItem(InteractionHand hand, CallbackInfo ci) {
        MinecraftMovement.INSTANCE.onStartUsingItem();
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void blackaddons$beforeAiStep(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (MinecraftMovement.isSprintBlocked()) {
            self.setSprinting(false);
        }

        Float silentYaw = MinecraftMovement.INSTANCE.getSilentYaw();
        Float silentPitch = MinecraftMovement.INSTANCE.getSilentPitch();
        if (silentYaw != null && silentPitch != null) {
            this.blackaddons$aiStepRealYaw = self.getYRot();
            this.blackaddons$aiStepRealPitch = self.getXRot();
            this.blackaddons$aiStepSilent = true;
            self.setYRot(silentYaw);
            self.setXRot(silentPitch);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void blackaddons$afterAiStep(CallbackInfo ci) {
        if (this.blackaddons$aiStepSilent) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            self.setYRot(this.blackaddons$aiStepRealYaw);
            self.setXRot(this.blackaddons$aiStepRealPitch);
            this.blackaddons$aiStepSilent = false;
        }
    }

    /** Keys are read in aiStep after the silent swap, input listeners get the real rotation back meanwhile. */
    @WrapOperation(
        method = "aiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V")
    )
    private void blackaddons$tickInputWithRealRotation(ClientInput input, Operation<Void> original) {
        if (!this.blackaddons$aiStepSilent) {
            original.call(input);
            return;
        }
        LocalPlayer self = (LocalPlayer) (Object) this;
        float silentYaw = self.getYRot();
        float silentPitch = self.getXRot();
        self.setYRot(this.blackaddons$aiStepRealYaw);
        self.setXRot(this.blackaddons$aiStepRealPitch);
        try {
            original.call(input);
        } finally {
            self.setYRot(silentYaw);
            self.setXRot(silentPitch);
        }
    }

    @WrapOperation(
        method = "aiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V")
    )
    private void blackaddons$setSprinting(LocalPlayer player, boolean sprinting, Operation<Void> original) {
        if (!sprinting || !MinecraftMovement.isSprintBlocked()) {
            original.call(player, sprinting);
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void blackaddons$beforeSendPosition(CallbackInfo ci) {
        Float silentYaw = MinecraftMovement.INSTANCE.getSilentYaw();
        Float silentPitch = MinecraftMovement.INSTANCE.getSilentPitch();
        if (silentYaw != null && silentPitch != null) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            this.blackaddons$sendPositionRealYaw = self.getYRot();
            this.blackaddons$sendPositionRealPitch = self.getXRot();
            this.blackaddons$sendPositionSilent = true;
            self.setYRot(silentYaw);
            self.setXRot(silentPitch);
        }
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void blackaddons$afterSendPosition(CallbackInfo ci) {
        if (this.blackaddons$sendPositionSilent) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            self.setYRot(this.blackaddons$sendPositionRealYaw);
            self.setXRot(this.blackaddons$sendPositionRealPitch);
            this.blackaddons$sendPositionSilent = false;
        }
        MinecraftMovement.INSTANCE.onPositionSent();
    }
}
