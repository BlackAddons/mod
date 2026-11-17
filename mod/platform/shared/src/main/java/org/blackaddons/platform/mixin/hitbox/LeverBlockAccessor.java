package org.blackaddons.platform.mixin.hitbox;

import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

/** Shape table built at construction, still vanilla when other mods enlarge levers via {@code getShape}. */
@Mixin(LeverBlock.class)
public interface LeverBlockAccessor {
    @Accessor("shapes")
    Function<BlockState, VoxelShape> blackaddons$getShapes();
}
