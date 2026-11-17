package org.blackaddons.platform.mixin.hitbox;

import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vanilla mushroom shape, which a {@code getShape} hook enlarging mushrooms doesn't touch. */
@Mixin(MushroomBlock.class)
public interface MushroomBlockAccessor {
    @Accessor("SHAPE")
    static VoxelShape blackaddons$getShape() {
        throw new AssertionError();
    }
}
