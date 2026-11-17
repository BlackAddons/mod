package org.blackaddons.platform.mixin.fme;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(SpriteSourceList.class)
public interface SpriteSourceListAccessor {
    @Accessor("sources")
    List<SpriteSource> blackaddons$getSources();
}
