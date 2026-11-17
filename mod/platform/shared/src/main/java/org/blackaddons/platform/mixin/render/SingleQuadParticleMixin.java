package org.blackaddons.platform.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.ParticleRenderEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin {

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onExtract(QuadParticleRenderState state, Camera camera, float partialTick, CallbackInfo ci) {
        ParticleAccessor accessor = (ParticleAccessor) this;
        ParticleRenderEvent event = new ParticleRenderEvent(accessor.getX(), accessor.getY(), accessor.getZ(), false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
        }
    }
}
