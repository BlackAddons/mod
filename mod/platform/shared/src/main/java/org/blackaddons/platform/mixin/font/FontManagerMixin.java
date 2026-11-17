package org.blackaddons.platform.mixin.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.blackaddons.platform.font.NickFontSets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
    @Shadow @Final private Map<Identifier, FontSet> fontSets;

    /**
     * Text can name a nick font that isn't built yet (restored chat, a line from before the download finished): build
     * it if we can, otherwise the game's font, never the missing-glyph boxes.
     */
    @Inject(method = "getFontSetRaw", at = @At("HEAD"), cancellable = true)
    private void blackaddons$unbuiltNickFont(Identifier id, CallbackInfoReturnable<FontSet> cir) {
        if (fontSets.containsKey(id) || !NickFontSets.INSTANCE.isNickFont(id)) return;
        NickFontSets.INSTANCE.buildIfKnown(id);
        FontSet set = fontSets.get(id);
        if (set == null) set = fontSets.get(Minecraft.DEFAULT_FONT);
        if (set != null) cir.setReturnValue(set);
    }
}
