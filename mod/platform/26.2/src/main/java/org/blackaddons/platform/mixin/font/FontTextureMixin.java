package org.blackaddons.platform.mixin.font;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.blackaddons.platform.font.MinecraftGlobalFonts;
import org.blackaddons.platform.font.SmoothGlyphBitmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.function.Supplier;

@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
    /* ImmediatelyFast's font_atlas_resizing rewrites it, so fuck it we ball */
    @Unique
    private int blackaddons$atlasSize;

    @Unique
    private boolean blackaddons$usesCustomAtlas;

    @Unique
    private String blackaddons$textureName;

    @Unique
    private boolean blackaddons$smoothPage;

    @Unique
    private int blackaddons$glyphX;

    @Unique
    private int blackaddons$glyphY;

    @ModifyArgs(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/FontTexture$Node;<init>(IIII)V"
        )
    )
    private void blackaddons$resizeCustomAtlasNode(
        Args args, Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored
    ) {
        String textureName = label.get();
        blackaddons$usesCustomAtlas = MinecraftGlobalFonts.useLinearSampling(textureName);
        if (!blackaddons$usesCustomAtlas) return;

        blackaddons$textureName = textureName;
        blackaddons$smoothPage = MinecraftGlobalFonts.getStitchingSmoothGlyph();
        blackaddons$atlasSize = MinecraftGlobalFonts.atlasSize(textureName, blackaddons$smoothPage);
        args.set(2, blackaddons$atlasSize);
        args.set(3, blackaddons$atlasSize);
    }

    @ModifyArgs(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;createTexture"
        )
    )
    private void blackaddons$resizeCustomAtlasTexture(Args args) {
        if (!blackaddons$usesCustomAtlas) return;

        args.set(1, args.<Integer>get(1) | GpuTexture.USAGE_RENDER_ATTACHMENT);
        args.set(3, blackaddons$atlasSize);
        args.set(4, blackaddons$atlasSize);
    }

    @ModifyArgs(
        method = "add",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/font/GlyphBitmap;upload(IILcom/mojang/blaze3d/textures/GpuTexture;)V"
        )
    )
    private void blackaddons$rememberCustomGlyphPosition(Args args) {
        if (!blackaddons$usesCustomAtlas) return;

        blackaddons$glyphX = args.get(0);
        blackaddons$glyphY = args.get(1);
    }

    @ModifyArgs(
        method = "add",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/glyphs/BakedSheetGlyph;<init>(Lcom/mojang/blaze3d/font/GlyphInfo;Lnet/minecraft/client/gui/font/GlyphRenderTypes;Lcom/mojang/blaze3d/textures/GpuTextureView;FFFFFFFF)V"
        )
    )
    private void blackaddons$useCustomAtlasCoordinates(Args args, GlyphInfo glyphInfo, GlyphBitmap glyph) {
        if (!blackaddons$usesCustomAtlas) return;

        float atlasSize = blackaddons$atlasSize;
        if (glyphInfo == SpecialGlyphs.WHITE) {
            float u = (blackaddons$glyphX + glyph.getPixelWidth() / 2F) / atlasSize;
            float v = (blackaddons$glyphY + glyph.getPixelHeight() / 2F) / atlasSize;
            args.set(3, u);
            args.set(4, u);
            args.set(5, v);
            args.set(6, v);
            return;
        }
        args.set(3, (blackaddons$glyphX + 0.01F) / atlasSize);
        args.set(4, (blackaddons$glyphX - 0.01F + glyph.getPixelWidth()) / atlasSize);
        args.set(5, (blackaddons$glyphY + 0.01F) / atlasSize);
        args.set(6, (blackaddons$glyphY - 0.01F + glyph.getPixelHeight()) / atlasSize);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void blackaddons$useLinearSampling(
        Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci
    ) {
        if (!blackaddons$usesCustomAtlas) return;
        sampler = RenderSystem.getSamplerCache().getClampToEdge(blackaddons$smoothPage ? FilterMode.LINEAR : FilterMode.NEAREST);
        MinecraftGlobalFonts.clearAtlasPage(texture);
        MinecraftGlobalFonts.recordAtlasPage(blackaddons$textureName, colored);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void blackaddons$keepPixelGlyphsSharp(
        GlyphInfo glyphInfo, GlyphBitmap glyph, CallbackInfoReturnable<BakedSheetGlyph> cir
    ) {
        if (blackaddons$usesCustomAtlas && blackaddons$smoothPage != (glyph instanceof SmoothGlyphBitmap)) {
            cir.setReturnValue(null);
        }
    }
}
