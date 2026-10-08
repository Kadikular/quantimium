package com.kadikular.quantimium.block;

import com.kadikular.quantimium.reactor.ReactorTraces;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * The Reactor's floor: dark board with circuit traces that run on from block to block, wherever it's
 * laid ({@link ReactorTraces}), lit once the Reactor forms.
 */
public class ReactorPlinthBlock extends ReactorPartBlock {

    public ReactorPlinthBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ReactorTraces.NORTH, 0).setValue(ReactorTraces.EAST, 0)
                .setValue(ReactorTraces.SOUTH, 0).setValue(ReactorTraces.WEST, 0).setValue(ReactorTraces.BUSY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ReactorTraces.NORTH, ReactorTraces.EAST, ReactorTraces.SOUTH, ReactorTraces.WEST, ReactorTraces.BUSY);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return ReactorTraces.at(defaultBlockState(), context.getClickedPos());
    }

    /** However it got here, it takes the traces of where it is. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        BlockState traced = ReactorTraces.at(state, pos);
        if (traced != state && !level.isClientSide()) level.setBlock(pos, traced, Block.UPDATE_CLIENTS);
    }
}
