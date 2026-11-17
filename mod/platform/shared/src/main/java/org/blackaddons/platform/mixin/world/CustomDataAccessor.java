package org.blackaddons.platform.mixin.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// copyTag() copies everything, and we read the Skyblock id per hotbar slot per tick.
@Mixin(CustomData.class)
public interface CustomDataAccessor {

    @Accessor("tag")
    CompoundTag blackaddons$getTag();
}
