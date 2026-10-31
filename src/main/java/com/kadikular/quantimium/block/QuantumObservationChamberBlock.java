package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.QuantumObservationChamberBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.kadikular.quantimium.block.entity.simulation.SideConfigWrench;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The Quantum Observation Chamber: the mid-tier chamber, collapsing Unrealised Matter on its own for FE,
 * with configurable faces. The work is in {@link QuantumObservationChamberBlockEntity}.
 */
public class QuantumObservationChamberBlock extends HorizontalMachineBlock {

    public static final MapCodec<QuantumObservationChamberBlock> CODEC = simpleCodec(QuantumObservationChamberBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public QuantumObservationChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    /** A wrench cycles the hit face's mode; anything else falls through to opening the screen. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof QuantumObservationChamberBlockEntity chamber)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return SideConfigWrench.handleUseItemOn(chamber, stack, level, player, hitResult);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumObservationChamberBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.QUANTUM_OBSERVATION_CHAMBER_BE.get(),
                QuantumObservationChamberBlockEntity::tick);
    }
}
