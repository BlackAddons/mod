package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.blackaddons.api.chat.ChatMotions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiMessage.Line.class)
public abstract class GuiMessageLineMixin {

    @Inject(method = "tag", at = @At("HEAD"), cancellable = true)
    private void blackaddons$hideIndicator(CallbackInfoReturnable<GuiMessageTag> cir) {
        if (ChatMotions.INSTANCE.getHidesIndicator()) cir.setReturnValue(null);
    }
}
