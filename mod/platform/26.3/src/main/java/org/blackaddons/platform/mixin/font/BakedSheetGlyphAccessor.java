package org.blackaddons.platform.mixin.font;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BakedSheetGlyph.class)
public interface BakedSheetGlyphAccessor {
    @Accessor("renderTypes")
    GlyphRenderTypes blackaddons$getRenderTypes();

    @Accessor("textureView")
    GpuTextureView blackaddons$getTextureView();

    @Accessor("u0")
    float blackaddons$getU0();

    @Accessor("u1")
    float blackaddons$getU1();

    @Accessor("v0")
    float blackaddons$getV0();

    @Accessor("v1")
    float blackaddons$getV1();
}
