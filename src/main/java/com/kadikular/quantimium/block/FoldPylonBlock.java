package com.kadikular.quantimium.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A corner of a Fold Chamber. Dark until the frame closes, when the fold core lights it: see
 * {@link com.kadikular.quantimium.fold.FoldChamber}.
 */
public class FoldPylonBlock extends Block {

    public FoldPylonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(QuantumFoundryStructure.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QuantumFoundryStructure.FORMED);
    }
}
