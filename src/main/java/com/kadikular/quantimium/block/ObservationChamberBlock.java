package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Stone-age Observation Chamber: a redstone pulse collapses a batch of Unrealised Matter into real
 * ores (and maybe Quantimium Trace). No FE and no side configs in v1.
 */
public class ObservationChamberBlock extends HorizontalMachineBlock {

    public static final MapCodec<ObservationChamberBlock> CODEC = simpleCodec(ObservationChamberBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public ObservationChamberBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ObservationChamberBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.OBSERVATION_CHAMBER_BE.get(),
                ObservationChamberBlockEntity::tick);
    }
}
