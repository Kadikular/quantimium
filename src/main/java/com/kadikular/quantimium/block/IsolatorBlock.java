package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.IsolatorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** An Isolator: a mirror that lets only power through. Pointed and set to redstone as a mirror is. */
public class IsolatorBlock extends LaserRelayBlock {
    public static final MapCodec<IsolatorBlock> ISOLATOR_CODEC = simpleCodec(IsolatorBlock::new);

    public IsolatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return ISOLATOR_CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IsolatorBlockEntity(pos, state);
    }
}
