package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.platform.cosmetics.NameReplacer;
import org.blackaddons.platform.event.impl.RenderOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*
 * HUD render hook. Wraps the caller's {@code Hud.extractRenderState} call instead of its tail: HUD-caching mods
 * (Gnetum) cancel the tail on most frames and draw into their own framebuffer. Still runs before BlackSkija's {@code
 * markHudLayer}.
 */
@Mixin(Gui.class)
public abstract class GuiHudMixin {

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
    )
    private void blackaddons$renderOverlay(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        NameReplacer.hudPass = true;
        try {
            original.call(hud, graphics, deltaTracker);
        } finally {
            NameReplacer.hudPass = false;
        }
        EventBus.INSTANCE.post(new RenderOverlayEvent(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }
}
