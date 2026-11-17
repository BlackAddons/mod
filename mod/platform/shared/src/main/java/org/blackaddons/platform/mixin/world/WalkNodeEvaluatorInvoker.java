package org.blackaddons.platform.mixin.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The game's own rules for dangerous blocks, so {@code BlockTerrain} doesn't keep a copy that drifts out of date. */
@Mixin(WalkNodeEvaluator.class)
public interface WalkNodeEvaluatorInvoker {
    @Invoker("getPathTypeFromState")
    static PathType blackaddons$pathTypeOf(BlockGetter level, BlockPos pos) {
        throw new AssertionError();
    }
}
