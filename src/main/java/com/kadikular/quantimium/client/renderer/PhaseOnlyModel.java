package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.client.ClientPhaseState;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/** Suppresses persistent mirror block quads outside the local player's phase. */
public final class PhaseOnlyModel extends DelegateBlockStateModel {

    public PhaseOnlyModel(BlockStateModel delegate) {
        super(delegate);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        if (ClientPhaseState.isActive()) super.collectParts(random, parts);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        if (ClientPhaseState.isActive()) super.collectParts(level, pos, state, random, parts);
    }
}
