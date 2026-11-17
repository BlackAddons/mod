package org.blackaddons.platform.mixin.hitbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.blackaddons.api.world.BlockHitboxOverride;
import org.blackaddons.api.world.BlockHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MushroomBlock.class)
public class MushroomBlockHitboxMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void blackaddons$enlargeShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        BlockHitboxOverride hitboxes = BlockHitboxes.getActive();
        if (hitboxes != null && hitboxes.getFullMushrooms()) cir.setReturnValue(Shapes.block());
    }
}
