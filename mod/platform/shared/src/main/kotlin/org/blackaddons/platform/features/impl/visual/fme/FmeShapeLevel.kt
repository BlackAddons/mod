package org.blackaddons.platform.features.impl.visual.fme

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

/** Block reads see the mesh region, so what FME draws. Everything else is the real level's. */
internal class FmeShapeLevel(private val region: BlockGetter, level: LevelReader) : LevelReader by level {
    override fun getBlockState(pos: BlockPos): BlockState = region.getBlockState(pos)
    override fun getFluidState(pos: BlockPos): FluidState = region.getFluidState(pos)
    override fun getBlockEntity(pos: BlockPos): BlockEntity? = region.getBlockEntity(pos)
}
