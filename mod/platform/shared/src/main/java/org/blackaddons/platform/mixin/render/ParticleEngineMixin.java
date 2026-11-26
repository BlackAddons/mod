package org.blackaddons.platform.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.ParticleSpawnEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Unique
    private static final ThreadLocal<Deque<Boolean>> blackaddons$particleCreationCancellations = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "createParticle", at = @At("HEAD"))
    private void blackaddons$onCreateParticle(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> cir) {
        String typeId = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString();
        ParticleSpawnEvent event = new ParticleSpawnEvent(typeId, x, y, z, false);
        EventBus.INSTANCE.post(event);
        blackaddons$particleCreationCancellations.get().push(event.getCancelled());
    }

    @WrapOperation(
        method = "createParticle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;add(Lnet/minecraft/client/particle/Particle;)V"
        )
    )
    private void blackaddons$addCreatedParticle(ParticleEngine engine, Particle particle, Operation<Void> original) {
        if (!blackaddons$particleCreationCancellations.get().peek()) {
            original.call(engine, particle);
        }
    }

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void blackaddons$clearParticleCreationCancellation(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> cir) {
        Deque<Boolean> cancellations = blackaddons$particleCreationCancellations.get();
        cancellations.pop();
        if (cancellations.isEmpty()) {
            blackaddons$particleCreationCancellations.remove();
        }
    }
}
