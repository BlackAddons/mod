package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.ArrayListDeque;
import org.blackaddons.api.chat.ChatMotions;
import org.blackaddons.api.chat.ChatRetentions;
import org.blackaddons.platform.compat.McCommonKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Unique private static long blackaddons$openedAt;

    @Inject(method = "init", at = @At("HEAD"))
    private void blackaddons$opened(CallbackInfo ci) {
        blackaddons$openedAt = System.nanoTime();
        ArrayListDeque<String> sent = McCommonKt.getChatComponent(Minecraft.getInstance()).getRecentChat();
        List<String> current = List.copyOf(sent);
        List<String> restored = ChatRetentions.INSTANCE.restoreSent(current);
        if (restored.equals(current)) return;
        sent.clear();
        sent.addAll(restored);
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void blackaddons$slideInputBackground(
            GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original
    ) {
        blackaddons$slid(graphics, () -> original.call(graphics, x0, y0, x1, y1, color));
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        )
    )
    private void blackaddons$slideInput(
            ChatScreen self, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Operation<Void> original
    ) {
        blackaddons$slid(graphics, () -> original.call(self, graphics, mouseX, mouseY, a));
    }

    @Unique
    private static void blackaddons$slid(GuiGraphicsExtractor graphics, Runnable draw) {
        float offset = ChatMotions.INSTANCE.inputOffset((System.nanoTime() - blackaddons$openedAt) / 1_000_000.0);
        if (offset == 0f) {
            draw.run();
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, offset);
        try {
            draw.run();
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
