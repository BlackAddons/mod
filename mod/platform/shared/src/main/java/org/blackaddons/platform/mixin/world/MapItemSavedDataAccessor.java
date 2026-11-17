package org.blackaddons.platform.mixin.world;

import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {
    @Accessor("decorations")
    Map<String, MapDecoration> blackaddons$getDecorations();
}
