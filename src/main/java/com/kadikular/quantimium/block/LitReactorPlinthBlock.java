package com.kadikular.quantimium.block;

/**
 * Reactor Plinth for building with: its traces always lit and running, with no Reactor to form. Not
 * part of a Reactor's floor; it crafts back into Reactor Plinth.
 */
public class LitReactorPlinthBlock extends ReactorPlinthBlock {

    public LitReactorPlinthBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(QuantumFoundryStructure.FORMED, true));
    }
}
