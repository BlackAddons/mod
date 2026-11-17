package org.blackaddons.platform.mixin.hitbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.blackaddons.api.world.BlockHitboxOverride;
import org.blackaddons.api.world.BlockHitboxes;
import org.blackaddons.platform.impl.world.BlockHitboxShapes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sorry Noamm, I just like being on top UwU
 * nothing personal, just business
 */
@Mixin(value = SkullBlock.class, priority = 1001)
public abstract class SkullBlockHitboxMixin extends AbstractSkullBlock {
    @Shadow @Final private static VoxelShape SHAPE;
    @Shadow @Final private static VoxelShape SHAPE_PIGLIN;

    private SkullBlockHitboxMixin(SkullBlock.Type type, Properties properties) {
        super(type, properties);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void blackaddons$enlargeShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        BlockHitboxOverride hitboxes = BlockHitboxes.getActive();
        if (hitboxes != null && hitboxes.getFullSecretSkulls() && BlockHitboxShapes.isSecretSkull(level, pos)) {
            cir.setReturnValue(Shapes.block());
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getType() == SkullBlock.Types.PIGLIN ? SHAPE_PIGLIN : SHAPE;
    }
}
