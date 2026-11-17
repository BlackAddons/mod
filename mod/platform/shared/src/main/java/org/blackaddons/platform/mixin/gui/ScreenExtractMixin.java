package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.platform.event.impl.RenderScreenBodyEvent;
import org.blackaddons.platform.event.impl.RenderScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@link RenderScreenBodyEvent} before a screen's tooltip and {@link RenderScreenEvent} after it draws. Targets
 * the final {@code extractRenderStateWithTooltipAndSubtitles}, so it runs even for screens that skip {@code super}. It
 * runs after the HUD, and that matters: BlackSkija only blits what's queued after the HUD above screens.
 */
@Mixin(Screen.class)
public abstract class ScreenExtractMixin {

    @Inject(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"
            )
    )
    private void blackaddons$beforeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                           float partialTick, CallbackInfo ci) {
        EventBus.INSTANCE.post(new RenderScreenBodyEvent((Screen) (Object) this, graphics, mouseX, mouseY));
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void blackaddons$afterScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        EventBus.INSTANCE.post(new RenderScreenEvent((Screen) (Object) this, partialTick));
    }
}
