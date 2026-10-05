package com.kadikular.quantimium.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A plain part of the Quantimium Reactor: its plinth or a Ring Emitter. Dark until the Horizon Core
 * finds the whole reactor, then lit. See {@link com.kadikular.quantimium.reactor.ReactorStructure}.
 */
public class ReactorPartBlock extends Block {

    public ReactorPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(QuantumFoundryStructure.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QuantumFoundryStructure.FORMED);
    }
}
