package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.StatusEffectTarget;
import org.blackaddons.api.event.render.StatusEffectsRenderEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectsInInventory.class)
public class EffectsInInventoryMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onExtractRenderState(GuiGraphicsExtractor guiGraphics, int i, int j, CallbackInfo ci) {
        StatusEffectsRenderEvent event = new StatusEffectsRenderEvent(StatusEffectTarget.INVENTORY, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
        }
    }
}
