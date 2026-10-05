package com.kadikular.quantimium.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Model holder for render-only hanging flora. Support checks still exist so the chunk-data
 * spawner can reuse vanilla plant-host rules without putting a block in the world.
 */
public final class MirrorHangingRootsBlock extends VegetationBlock {

    public static final MapCodec<MirrorHangingRootsBlock> CODEC = simpleCodec(MirrorHangingRootsBlock::new);

    public MirrorHangingRootsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return Block.isFaceFull(state.getBlockSupportShape(level, pos), Direction.DOWN)
                || Block.isFaceFull(state.getCollisionShape(level, pos), Direction.DOWN);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        return mayPlaceOn(level.getBlockState(above), level, above);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return null;
    }
}
