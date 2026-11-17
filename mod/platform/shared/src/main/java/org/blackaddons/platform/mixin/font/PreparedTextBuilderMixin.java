package org.blackaddons.platform.mixin.font;

import net.minecraft.client.gui.font.TextRenderable;
import org.blackaddons.platform.font.MinecraftGlobalFonts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class PreparedTextBuilderMixin {
    @Shadow @Final private int backgroundColor;
    @Shadow private float backgroundLeft;
    @Shadow private float backgroundTop;
    @Shadow private float backgroundRight;
    @Shadow private float backgroundBottom;

    @Inject(method = "addGlyph", at = @At("HEAD"))
    private void blackaddons$fitBackgroundToGlyph(TextRenderable.Styled instance, CallbackInfo ci) {
        if (backgroundColor >>> 24 == 0 || !MinecraftGlobalFonts.isEnabled()) return;
        backgroundLeft = Math.min(backgroundLeft, instance.left() - 1F);
        backgroundTop = Math.min(backgroundTop, instance.top() - 1F);
        backgroundRight = Math.max(backgroundRight, instance.right());
        backgroundBottom = Math.max(backgroundBottom, instance.bottom());
    }
}
