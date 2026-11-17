package org.blackaddons.platform.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.blackaddons.platform.cosmetics.NameReplacer;
import org.blackaddons.platform.font.GlobalFontRenderContext;
import org.blackaddons.platform.font.MinecraftGlobalFonts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextRenderState.class)
public abstract class GuiTextRenderStateMixin {
    /** Text is prepared after extraction, so we capture here whether it came from the HUD. */
    @Unique private boolean blackaddons$hudText;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void blackaddons$captureHud(CallbackInfo ci) {
        blackaddons$hudText = NameReplacer.INSTANCE.inHudPass();
    }

    @ModifyArg(
        method = "ensurePrepared",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;")
    )
    private FormattedCharSequence blackaddons$hudNames(FormattedCharSequence text) {
        return blackaddons$hudText ? NameReplacer.INSTANCE.decorateSequence(text) : text;
    }

    @WrapMethod(method = "ensurePrepared")
    private Font.PreparedText blackaddons$prepareScreenGlyphs(Operation<Font.PreparedText> original) {
        if (!MinecraftGlobalFonts.isMinecraftEnabled()) return original.call();
        GuiTextRenderState state = (GuiTextRenderState) (Object) this;
        int previous = GlobalFontRenderContext.beginScreen(Math.max(
            length(state.pose.m00(), state.pose.m01()),
            length(state.pose.m10(), state.pose.m11())
        ));
        try {
            return original.call();
        } finally {
            GlobalFontRenderContext.restore(previous);
        }
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt((double) x * x + (double) y * y);
    }
}
