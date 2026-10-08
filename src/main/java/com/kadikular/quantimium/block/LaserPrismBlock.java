package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserPrismBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A laser prism: links to blocks with the Laser Linker, and sends what light reaches it to each. */
public class LaserPrismBlock extends BaseEntityBlock {
    public static final MapCodec<LaserPrismBlock> CODEC = simpleCodec(LaserPrismBlock::new);

    public LaserPrismBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserPrismBlockEntity(pos, state);
    }
}
