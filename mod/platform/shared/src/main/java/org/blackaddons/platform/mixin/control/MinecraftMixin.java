package org.blackaddons.platform.mixin.control;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.blackaddons.platform.impl.player.MinecraftInteraction;
import org.blackaddons.platform.impl.player.MinecraftMovement;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    @Nullable
    public Entity crosshairPickEntity;

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Unique
    private HitResult blackaddons$savedHitResult;

    @Unique
    private Entity blackaddons$savedCrosshairEntity;

    @Unique
    private boolean blackaddons$hasSavedHitResult;

    // After gameMode.tick() flushed the hotbar slot, before the movement packet, and never with a screen open.
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void blackaddons$sendQueuedClicks(CallbackInfo ci) {
        MinecraftInteraction.INSTANCE.sendQueued();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void blackaddons$beforeStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (MinecraftInteraction.INSTANCE.shouldDeferAttack()) {
            cir.setReturnValue(false);
            return;
        }
        this.blackaddons$prepareSilentHit();
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void blackaddons$afterStartAttack(CallbackInfoReturnable<Boolean> cir) {
        this.blackaddons$restoreHitResult();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void blackaddons$beforeStartUseItem(CallbackInfo ci) {
        if (MinecraftInteraction.INSTANCE.shouldDeferUse()) {
            ci.cancel();
            return;
        }
        this.blackaddons$prepareSilentHit();
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void blackaddons$afterStartUseItem(CallbackInfo ci) {
        this.blackaddons$restoreHitResult();
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void blackaddons$beforeContinueAttack(boolean flag, CallbackInfo ci) {
        if (flag && MinecraftInteraction.INSTANCE.shouldDeferContinuousAttack()) {
            ci.cancel();
            return;
        }
        this.blackaddons$prepareSilentHit();
        if (flag && MinecraftInteraction.INSTANCE.stopsMining()) {
            this.blackaddons$restoreHitResult();
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("RETURN"))
    private void blackaddons$afterContinueAttack(boolean flag, CallbackInfo ci) {
        this.blackaddons$restoreHitResult();
    }

    @Unique
    private void blackaddons$prepareSilentHit() {
        Float silentYaw = MinecraftMovement.INSTANCE.getSilentYaw();
        Float silentPitch = MinecraftMovement.INSTANCE.getSilentPitch();
        if (silentYaw != null && silentPitch != null && this.player != null) {
            this.blackaddons$savedHitResult = this.hitResult;
            this.blackaddons$savedCrosshairEntity = this.crosshairPickEntity;
            this.blackaddons$hasSavedHitResult = true;

            HitResult silentHit = MinecraftInteraction.INSTANCE.raycastSilent(silentYaw, silentPitch);
            this.hitResult = silentHit;
            this.crosshairPickEntity = silentHit instanceof EntityHitResult
                ? ((EntityHitResult) silentHit).getEntity()
                : null;
        }
    }

    @Unique
    private void blackaddons$restoreHitResult() {
        if (this.blackaddons$hasSavedHitResult) {
            this.hitResult = this.blackaddons$savedHitResult;
            this.crosshairPickEntity = this.blackaddons$savedCrosshairEntity;
            this.blackaddons$savedHitResult = null;
            this.blackaddons$savedCrosshairEntity = null;
            this.blackaddons$hasSavedHitResult = false;
        }
    }
}
