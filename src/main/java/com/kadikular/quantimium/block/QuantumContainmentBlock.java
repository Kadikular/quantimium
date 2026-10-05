// Path: src/main/java/com/kadikular/quantimium/block/QuantumContainmentBlock.java
package com.kadikular.quantimium.block;

import net.minecraft.server.level.ServerLevel;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.TriState;

public class QuantumContainmentBlock extends Block {

    public QuantumContainmentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0f;
    }

    /**
     * The simulator's renderer draws the containment field itself, so the block contributes no geometry.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * The field is an energy volume, not a surface, so nothing may root in it.
     */
    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soilPosition,
                                    Direction facing, BlockState plant) {
        return TriState.FALSE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos.below()) instanceof QuantumSimulatorBlockEntity simulator) {
                player.openMenu(simulator, pos.below());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos.below()) instanceof QuantumSimulatorBlockEntity simulator) {
            // HYPERVISOR GUARD: Do not disengage if the virtual machine caused the block replacement!
            if (simulator.isEngaged() && !simulator.isVirtualTicking()) {
                simulator.onContainmentLost();
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}