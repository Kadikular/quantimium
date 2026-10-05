package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Quantimium Reactor's controller: a cage on a dais in the middle of the plinth. Seat a
 * Singularity in it and the Reactor forms; the Singularity holds everything the horizon holds, and
 * takes it along when it leaves. See {@link HorizonCoreBlockEntity}.
 */
public class HorizonCoreBlock extends BaseEntityBlock {
    public static final MapCodec<HorizonCoreBlock> CODEC = simpleCodec(HorizonCoreBlock::new);
    /** Whether a Singularity sits in the cage. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty SEATED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("seated");

    public HorizonCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(QuantumFoundryStructure.FORMED, false).setValue(SEATED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QuantumFoundryStructure.FORMED, SEATED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** A Singularity goes into the empty cage. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(com.kadikular.quantimium.init.ModItems.SINGULARITY.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HorizonCoreBlockEntity core && core.seat(stack)) {
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.5f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Opens the core's screen; sneaking with an empty hand takes the Singularity out instead. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HorizonCoreBlockEntity core) {
            if (player.isSecondaryUseActive() && core.isSeated()) {
                net.minecraft.world.item.ItemStack singularity = core.unseat();
                if (!player.getInventory().add(singularity)) player.drop(singularity, false);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.5f);
            } else {
                player.openMenu(core, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HorizonCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.HORIZON_CORE_BE.get(), HorizonCoreBlockEntity::serverTick);
    }
}
