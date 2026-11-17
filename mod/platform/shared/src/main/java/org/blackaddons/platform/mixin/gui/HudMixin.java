package org.blackaddons.platform.mixin.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.chat.ActionBarEvent;
import org.blackaddons.api.event.render.StatusEffectTarget;
import org.blackaddons.api.event.render.StatusEffectsRenderEvent;
import org.blackaddons.api.event.render.TabListVisibilityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Action-bar interceptor and HUD element toggles. 26.2 moved these from {@code Gui} to {@code Hud}, the render hook is
 * in {@code GuiHudMixin}.
 */
@Mixin(Hud.class)
public abstract class HudMixin {

    // Stops our own re-dispatch from looping.
    @Unique private boolean blackaddons$rewriting;

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void blackaddons$actionBar(Component string, boolean animate, CallbackInfo ci) {
        if (blackaddons$rewriting) return;
        String raw = string.getString();
        String unformatted = ChatFormatting.stripFormatting(raw);
        ActionBarEvent event = new ActionBarEvent(raw, unformatted != null ? unformatted : raw, false, null);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
            return;
        }
        String repl = event.getReplacement();
        if (repl != null && !repl.equals(raw)) {
            ci.cancel();
            if (!repl.isBlank()) {
                blackaddons$rewriting = true;
                try {
                    ((Hud) (Object) this).setOverlayMessage(Component.literal(repl), animate);
                } finally {
                    blackaddons$rewriting = false;
                }
            }
        }
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void blackaddons$extractEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        StatusEffectsRenderEvent event = new StatusEffectsRenderEvent(StatusEffectTarget.HUD, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
        }
    }

    /**
     * Optional: Lunar Client rewrites {@code Gui} without this method, so a hard requirement crashes on launch. If
     * Lunar causes issues again, drop support for Lunar Client.
     */
    @Redirect(method = "extractTabList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"), require = 0)
    private boolean blackaddons$onPlayerListKeyIsDown(KeyMapping keyMapping) {
        TabListVisibilityEvent event = new TabListVisibilityEvent(keyMapping.isDown());
        EventBus.INSTANCE.post(event);
        return event.getVisible();
    }
}
