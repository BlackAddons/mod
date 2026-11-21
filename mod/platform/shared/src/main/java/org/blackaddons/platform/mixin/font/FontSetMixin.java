package org.blackaddons.platform.mixin.font;

import net.minecraft.client.gui.font.FontSet;
import org.blackaddons.platform.font.FontSetResets;
import org.blackaddons.platform.font.PreparedWorldTextCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FontSet.class)
public abstract class FontSetMixin {
    @Inject(method = {"resetTextures", "close"}, at = @At("HEAD"))
    private void blackaddons$dropPreparedWorldText(CallbackInfo ci) {
        PreparedWorldTextCache.invalidate();
        FontSetResets.reset();
    }
}
