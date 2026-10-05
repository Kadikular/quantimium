package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.RelayModuleBlockEntity;
import com.kadikular.quantimium.superposition.PodModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Relay module: a cradle edge that holds Tesseracts bound to other pods, and makes its pod a hub.
 * Use it to open its hold. See {@link com.kadikular.quantimium.superposition.Relay}.
 */
public class RelayModuleBlock extends PodModuleBlock implements EntityBlock {

    public RelayModuleBlock(Properties properties) {
        super(PodModule.RELAY, properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RelayModuleBlockEntity relay) {
            player.openMenu(relay, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayModuleBlockEntity(pos, state);
    }
}
