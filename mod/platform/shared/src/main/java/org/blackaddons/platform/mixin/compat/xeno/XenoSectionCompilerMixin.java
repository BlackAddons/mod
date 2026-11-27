package org.blackaddons.platform.mixin.compat.xeno;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.xeno.client.common.render.chunk.XenoSectionCompiler", remap = false)
public abstract class XenoSectionCompilerMixin {
    @WrapOperation(
        method = "compile",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/PalettedContainer;get(III)Ljava/lang/Object;"
        ),
        remap = false
    )
    private Object blackaddons$remapFmeState(
        PalettedContainer<BlockState> container,
        int relX,
        int relY,
        int relZ,
        Operation<Object> original,
        @Local(argsOnly = true) SectionPos sectionPos,
        @Local(argsOnly = true) RenderSectionRegion region
    ) {
        BlockState state = (BlockState) original.call(container, relX, relY, relZ);
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return state;
        }
        int x = sectionPos.minBlockX() + relX;
        int y = sectionPos.minBlockY() + relY;
        int z = sectionPos.minBlockZ() + relZ;
        return FmeBlockRemapper.remap(state, x, y, z, region, level);
    }
}
