package org.blackaddons.platform.mixin.render;

import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.VisionObstruction;
import org.blackaddons.api.event.render.VisionObstructionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectFogEnvironment.class)
public abstract class MobEffectFogEnvironmentMixin {

    @Shadow
    public abstract Holder<MobEffect> getMobEffect();

    @Inject(method = "isApplicable", at = @At("RETURN"), cancellable = true)
    private void blackaddons$clearEffectFog(FogType fogType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        Holder<MobEffect> effect = getMobEffect();
        VisionObstruction obstruction = effect == MobEffects.BLINDNESS ? VisionObstruction.BLINDNESS
            : effect == MobEffects.DARKNESS ? VisionObstruction.DARKNESS
            : null;
        if (obstruction != null && EventBus.INSTANCE.post(new VisionObstructionEvent(obstruction, false)).getCancelled()) {
            cir.setReturnValue(false);
        }
    }
}
