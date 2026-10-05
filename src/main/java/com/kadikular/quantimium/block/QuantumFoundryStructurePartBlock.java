package com.kadikular.quantimium.block;

import net.minecraft.server.level.ServerLevel;
import com.kadikular.quantimium.block.multiblock.MultiblockParts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Decorative arm part that immediately asks nearby controllers to re-form. */
public class QuantumFoundryStructurePartBlock extends Block {
    public QuantumFoundryStructurePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(QuantumFoundryStructure.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QuantumFoundryStructure.FORMED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) MultiblockParts.notifyNearby(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        MultiblockParts.notifyNearby(level, pos);
    }
}
