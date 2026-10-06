package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.kadikular.quantimium.reactor.ReactorTraces;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A window in the Reactor's plinth onto a pocket of void, holding up to
 * {@link CatalystBayBlockEntity#SLOTS} catalysts: machines, crafting tables, Folded Tesseracts. Their
 * recipes join the Reactor's. Use it to open its screen, which holds the catalysts (one to each quarter
 * of the window) and its filter. The plinth's traces run into its frame.
 */
public class CatalystBayBlock extends BaseEntityBlock {
    public static final MapCodec<CatalystBayBlock> CODEC = simpleCodec(CatalystBayBlock::new);

    public CatalystBayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(QuantumFoundryStructure.FORMED, false)
                .setValue(ReactorTraces.NORTH, 0).setValue(ReactorTraces.EAST, 0)
                .setValue(ReactorTraces.SOUTH, 0).setValue(ReactorTraces.WEST, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(QuantumFoundryStructure.FORMED, ReactorTraces.NORTH, ReactorTraces.EAST, ReactorTraces.SOUTH,
                ReactorTraces.WEST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return ReactorTraces.at(defaultBlockState(), context.getClickedPos());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        BlockState traced = ReactorTraces.at(state, pos);
        if (traced != state && !level.isClientSide()) level.setBlock(pos, traced, Block.UPDATE_CLIENTS);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Using the bay, with anything in hand, opens it: catalysts go in and out only through its screen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CatalystBayBlockEntity bay) {
            player.openMenu(bay, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatalystBayBlockEntity(pos, state);
    }
}
