package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A glass case on the Reactor's plinth holding one catalyst: a machine, a crafting table, a Folded
 * Tesseract. Its recipes join the Reactor's. Use an item on it to put it in; an empty hand takes it out.
 */
public class CatalystBayBlock extends BaseEntityBlock {
    public static final MapCodec<CatalystBayBlock> CODEC = simpleCodec(CatalystBayBlock::new);
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 15, 15);

    public CatalystBayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CatalystBayBlockEntity bay) || !bay.getCatalyst().isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) bay.setCatalyst(stack.split(1));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CatalystBayBlockEntity bay
                && !bay.getCatalyst().isEmpty()) {
            ItemStack taken = bay.getCatalyst();
            bay.setCatalyst(ItemStack.EMPTY);
            if (!player.getInventory().add(taken)) player.drop(taken, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatalystBayBlockEntity(pos, state);
    }
}
