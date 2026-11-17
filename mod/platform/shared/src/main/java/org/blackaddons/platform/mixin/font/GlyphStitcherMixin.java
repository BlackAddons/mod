package org.blackaddons.platform.mixin.font;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.blackaddons.platform.font.MinecraftGlobalFonts;
import org.blackaddons.platform.font.SmoothGlyphBitmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.function.Supplier;

@Mixin(GlyphStitcher.class)
public abstract class GlyphStitcherMixin {
    @Inject(method = "stitch", at = @At("HEAD"))
    private void blackaddons$rememberGlyphKind(
        GlyphInfo info, GlyphBitmap glyph, CallbackInfoReturnable<BakedSheetGlyph> cir
    ) {
        MinecraftGlobalFonts.setStitchingSmoothGlyph(glyph instanceof SmoothGlyphBitmap);
    }

    @ModifyArgs(
        method = "stitch",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/FontTexture;<init>(Ljava/util/function/Supplier;Lnet/minecraft/client/gui/font/GlyphRenderTypes;Z)V"
        )
    )
    private void blackaddons$drawGlyphsInEmitOrder(Args args) {
        String textureName = args.<Supplier<String>>get(0).get();
        if (!MinecraftGlobalFonts.useLinearSampling(textureName)) return;
        args.set(1, MinecraftGlobalFonts.inEmitOrder(textureName, args.get(1), args.<Boolean>get(2)));
    }
}
