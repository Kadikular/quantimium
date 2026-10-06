package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.kadikular.quantimium.reactor.ReactorTraces;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * recipes join the Reactor's. Each quarter of the window is a slot: use an item on one to put it in.
 * An empty hand opens the bay's screen, to take catalysts out and set its filter. The plinth's traces
 * run into its frame.
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

    /** An item goes straight into the quarter clicked, or the next free one. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof CatalystBayBlockEntity bay)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        int slot = bay.slotToFill(slotAt(pos, hitResult));
        if (slot < 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) bay.setCatalyst(slot, stack.split(1));
        return InteractionResult.SUCCESS;
    }

    /** An empty hand opens the bay: its catalysts and its filter. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CatalystBayBlockEntity bay) {
            player.openMenu(bay, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** The quarter of the window a click on the top lands in, or -1 for any other face. */
    private static int slotAt(BlockPos pos, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP) return -1;
        double x = hit.getLocation().x - pos.getX();
        double z = hit.getLocation().z - pos.getZ();
        return CatalystBayBlockEntity.slotAt(x, z);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatalystBayBlockEntity(pos, state);
    }
}
