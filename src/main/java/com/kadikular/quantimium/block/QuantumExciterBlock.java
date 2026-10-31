package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.QuantumExciterBlockEntity;
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

/** Burns FE to raise / hold neighbourhood flux at Medium. No GUI — meter + ACTIVE glow is enough. */
public class QuantumExciterBlock extends HorizontalMachineBlock {

    public static final MapCodec<QuantumExciterBlock> CODEC = simpleCodec(QuantumExciterBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public QuantumExciterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    /** A click switches what it aims for: Medium flux, or High. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                  net.minecraft.world.entity.player.Player player,
                                                                  net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof QuantumExciterBlockEntity exciter) {
            exciter.setAimsHigh(!exciter.aimsHigh());
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(exciter.aimsHigh()
                    ? "message.quantimium.quantum_exciter.high" : "message.quantimium.quantum_exciter.medium")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumExciterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.QUANTUM_EXCITER_BE.get(), QuantumExciterBlockEntity::tick);
    }
}
