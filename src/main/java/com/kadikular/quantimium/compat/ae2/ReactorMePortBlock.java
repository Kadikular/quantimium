package com.kadikular.quantimium.compat.ae2;

import com.kadikular.quantimium.block.ReactorPortBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ME Superposition Port: a Reactor port on the plinth's rim that joins an ME network. Using it opens
 * its screen, where it's set up like a storage bus.
 */
public class ReactorMePortBlock extends ReactorPortBlock {
    public static final MapCodec<ReactorMePortBlock> CODEC = simpleCodec(ReactorMePortBlock::new);

    public ReactorMePortBlock(Properties properties) {
        super(properties, Kind.ME);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Using the port opens its screen: mode, priority, storing, and its filter. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ReactorMePortBlockEntity port) {
            player.openMenu(port, pos);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorMePortBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, Ae2Content.REACTOR_ME_PORT_BE.get(), ReactorMePortBlockEntity::serverTick);
    }
}
