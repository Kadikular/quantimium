package com.kadikular.quantimium.block;

import net.minecraft.server.level.ServerLevel;
import com.kadikular.quantimium.superposition.PodStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The frame of a Superposition Pod's cradle: its four corners and the block the capsule stands on. See
 * {@link PodStructure}. Lit while its pod is whole.
 */
public class PodCradleBlock extends Block {

    /** Part of a whole pod. Shared with {@link PodModuleBlock}. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public PodCradleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) PodStructure.changed(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        PodStructure.changed(level, pos);
    }
}
