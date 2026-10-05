package com.kadikular.quantimium.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Model holder for two-high render-only mirror grass. */
public final class MirrorTallGrassBlock extends DoublePlantBlock {

    public static final MapCodec<MirrorTallGrassBlock> CODEC = simpleCodec(MirrorTallGrassBlock::new);

    public MirrorTallGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends DoublePlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return Block.isFaceFull(state.getBlockSupportShape(level, pos), Direction.UP)
                || Block.isFaceFull(state.getCollisionShape(level, pos), Direction.UP);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return null;
    }
}
