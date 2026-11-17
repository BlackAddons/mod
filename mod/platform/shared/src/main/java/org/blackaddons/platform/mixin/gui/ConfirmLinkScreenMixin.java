package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.LinkConfirmationEvent;
import org.blackaddons.platform.compat.NativeDesktop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

@Mixin(ConfirmLinkScreen.class)
public class ConfirmLinkScreenMixin {

    @Inject(method = "confirmLinkNow(Lnet/minecraft/client/gui/screens/Screen;Ljava/net/URI;Z)V", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$onConfirmLinkNowUri(Screen screen, URI uri, boolean openImmediately, CallbackInfo ci) {
        LinkConfirmationEvent event = new LinkConfirmationEvent(uri.toString(), false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            NativeDesktop.INSTANCE.openUri(uri);
            ci.cancel();
        }
    }

    @Inject(method = "confirmLinkNow(Lnet/minecraft/client/gui/screens/Screen;Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void blackaddons$onConfirmLinkNowString(Screen screen, String url, boolean openImmediately, CallbackInfo ci) {
        LinkConfirmationEvent event = new LinkConfirmationEvent(url, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            NativeDesktop.INSTANCE.openUri(url);
            ci.cancel();
        }
    }
}
