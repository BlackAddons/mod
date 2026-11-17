package org.blackaddons.platform.mixin.hitbox;

import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vanilla floor-skull shapes, which a {@code getShape} hook enlarging skulls doesn't touch. */
@Mixin(SkullBlock.class)
public interface SkullBlockAccessor {
    @Accessor("SHAPE")
    static VoxelShape blackaddons$getShape() {
        throw new AssertionError();
    }

    @Accessor("SHAPE_PIGLIN")
    static VoxelShape blackaddons$getPiglinShape() {
        throw new AssertionError();
    }
}
