package org.blackaddons.platform.mixin.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.input.MouseClickEvent;
import org.blackaddons.api.event.input.MouseScrollEvent;
import org.blackaddons.platform.compat.McCommonKt;
import org.blackaddons.platform.compat.McCompatKt;
import org.blackaddons.platform.impl.gui.MinecraftContainerOverlays;
import org.blackaddons.platform.impl.input.MinecraftKeyNames;
import org.blackaddons.platform.ui.screen.ScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow private boolean mouseGrabbed;

    @Unique private double blackaddons$savedX;
    @Unique private double blackaddons$savedY;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onMouseButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        MouseHandler mouse = Minecraft.getInstance().mouseHandler;
        MinecraftKeyNames names = MinecraftKeyNames.INSTANCE;
        if (ScreenOverlay.INSTANCE.mouseButton(names.button(rawButtonInfo.button()), names.action(action))) {
            ci.cancel();
            return;
        }
        MouseClickEvent event = new MouseClickEvent(mouse.xpos(), mouse.ypos(), names.button(rawButtonInfo.button()),
            names.action(action), names.modifiers(rawButtonInfo.modifiers()), false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) ci.cancel();
    }

    /**
     * A storage page relay swaps screens through grabMouse/releaseMouse, which center the cursor. Save the position and
     * restore it when a storage overlay opens. Only on the first grab, later calls see the already-centered position.
     */
    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void blackaddons$saveCursor(CallbackInfo ci) {
        if (!this.mouseGrabbed) {
            blackaddons$savedX = this.xpos;
            blackaddons$savedY = this.ypos;
        }
    }

    @Inject(method = "releaseMouse", at = @At("TAIL"))
    private void blackaddons$restoreCursor(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (McCommonKt.getCurrentScreen(minecraft) instanceof AbstractContainerScreen<?> screen
            && MinecraftContainerOverlays.INSTANCE.keepsCursor(screen)) {
            this.xpos = blackaddons$savedX;
            this.ypos = blackaddons$savedY;
            McCompatKt.releaseCursorAt(minecraft, blackaddons$savedX, blackaddons$savedY);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (ScreenOverlay.INSTANCE.scroll(yoffset)) {
            ci.cancel();
            return;
        }
        MouseScrollEvent event = new MouseScrollEvent(xoffset, yoffset, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
        }
    }
}
