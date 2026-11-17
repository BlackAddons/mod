package org.blackaddons.platform.mixin.font;

import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.blackaddons.platform.font.GlobalFontEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class FontOutlineMixin {
    @Inject(method = "prepare8xTextOutline", at = @At("HEAD"))
    private void blackaddons$beginOutline(
        FormattedCharSequence text, float x, float y, int color,
        CallbackInfoReturnable<Font.PreparedText> cir
    ) {
        GlobalFontEffects.beginVanillaOutline();
    }

    @Inject(method = "prepare8xTextOutline", at = @At("RETURN"))
    private void blackaddons$endOutline(
        FormattedCharSequence text, float x, float y, int color,
        CallbackInfoReturnable<Font.PreparedText> cir
    ) {
        GlobalFontEffects.endVanillaOutline();
    }
}
