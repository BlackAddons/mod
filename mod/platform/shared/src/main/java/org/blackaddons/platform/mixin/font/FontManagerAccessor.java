package org.blackaddons.platform.mixin.font;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Accessor("fontSets")
    Map<Identifier, FontSet> blackaddons$getFontSets();

    @Accessor("providersToClose")
    List<GlyphProvider> blackaddons$getProvidersToClose();

    @Invoker("createFontSet")
    FontSet blackaddons$createFontSet(Identifier id, List<GlyphProvider.Conditional> providers, Set<FontOption> options);

    @Invoker("getFontOptions")
    static Set<FontOption> blackaddons$getFontOptions(Options options) {
        throw new AssertionError();
    }
}
