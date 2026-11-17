package org.blackaddons.platform.mixin.font;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.blackaddons.platform.cosmetics.NameReplacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** HUD text is measured as drawn: {@code GuiTextRenderStateMixin} swaps cosmetic names in at prepare time. */
@Mixin(Font.class)
public abstract class FontWidthMixin {

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence blackaddons$hudSequenceWidth(FormattedCharSequence text) {
        return NameReplacer.INSTANCE.inHudPass() ? NameReplacer.INSTANCE.decorateSequence(text) : text;
    }

    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
    private void blackaddons$hudTextWidth(FormattedText text, CallbackInfoReturnable<Integer> cir) {
        if (!NameReplacer.INSTANCE.inHudPass()) return;
        FormattedCharSequence sequence = Language.getInstance().getVisualOrder(text);
        FormattedCharSequence shown = NameReplacer.INSTANCE.decorateSequence(sequence);
        if (shown != sequence) cir.setReturnValue(((Font) (Object) this).width(shown));
    }
}
