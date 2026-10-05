package com.kadikular.quantimium.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pylon of the Unfolding Array: one at each corner of its pad. Its crystal is what reaches into the
 * person on the pad while they are unfolded.
 */
public class ArrayPylonBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public ArrayPylonBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
