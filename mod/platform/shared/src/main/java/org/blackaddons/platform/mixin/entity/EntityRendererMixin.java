package org.blackaddons.platform.mixin.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.blackaddons.platform.cosmetics.BlackaddonsRenderState;
import org.blackaddons.platform.cosmetics.NameReplacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per entity per frame: stashes the UUID and, where vanilla already shows the name, swaps {@code state.nameTag} for the
 * cosmetic name. Rewriting the final field keeps vanilla's visibility rules (sneak, distance, F1).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void blackaddons$customize(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        ((BlackaddonsRenderState) state).setBlackaddonsUuid(entity.getUUID());

        if (state.nameTag != null && entity instanceof Player) {
            state.nameTag = NameReplacer.INSTANCE.decorate(state.nameTag);
        }
    }
}
