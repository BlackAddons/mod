package org.blackaddons.platform.mixin.font;

import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import org.blackaddons.platform.font.FontSetResets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Vanilla clears this cache only after its own font reloads. Ours (global font, nick fonts) reload sets directly, so the
 * cached white glyph kept sampling a freed atlas page under every underline, strikethrough and text background.
 */
@Mixin(targets = "net.minecraft.client.gui.font.FontManager$CachedFontProvider")
public abstract class CachedFontProviderMixin {
    @Unique
    private int blackaddons$seenResets = FontSetResets.getCount();

    @Shadow
    public abstract void invalidate();

    @Inject(method = "glyphs", at = @At("HEAD"))
    private void blackaddons$dropStaleSource(FontDescription description, CallbackInfoReturnable<GlyphSource> cir) {
        blackaddons$dropStale();
    }

    @Inject(method = "effect", at = @At("HEAD"))
    private void blackaddons$dropStaleEffect(CallbackInfoReturnable<EffectGlyph> cir) {
        blackaddons$dropStale();
    }

    @Unique
    private void blackaddons$dropStale() {
        int resets = FontSetResets.getCount();
        if (resets == blackaddons$seenResets) return;
        blackaddons$seenResets = resets;
        invalidate();
    }
}
