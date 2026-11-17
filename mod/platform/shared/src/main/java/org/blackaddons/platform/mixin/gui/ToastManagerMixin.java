package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.ToastAddEvent;
import org.blackaddons.api.event.render.ToastType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public class ToastManagerMixin {

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onAddToast(Toast toast, CallbackInfo ci) {
        ToastType type = ToastType.OTHER;
        if (toast instanceof TutorialToast) {
            type = ToastType.TUTORIAL;
        } else if (toast instanceof SystemToast systemToast && systemToast.getToken() == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) {
            type = ToastType.UNSECURE_SERVER_WARNING;
        }

        ToastAddEvent event = new ToastAddEvent(type, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
        }
    }
}
