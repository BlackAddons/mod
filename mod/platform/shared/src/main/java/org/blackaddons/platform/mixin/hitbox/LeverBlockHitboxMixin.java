package org.blackaddons.platform.mixin.hitbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.blackaddons.api.world.BlockHitboxOverride;
import org.blackaddons.api.world.BlockHitboxes;
import org.blackaddons.platform.impl.world.BlockHitboxShapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LeverBlock.class)
public class LeverBlockHitboxMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void blackaddons$enlargeShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        BlockHitboxOverride hitboxes = BlockHitboxes.getActive();
        if (hitboxes == null) return;
        Double size = hitboxes.leverSize(pos.getX(), pos.getY(), pos.getZ());
        if (size != null) cir.setReturnValue(BlockHitboxShapes.lever(state, size));
    }
}
