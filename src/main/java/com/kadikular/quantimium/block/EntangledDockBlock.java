package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.EntangledDockBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A pedestal that superposes a charged item with itself (see {@link EntangledDockBlockEntity}). Use
 * it with an item that holds FE to entangle it; with an empty hand to see how it is doing; sneaking
 * with an empty hand to let it go.
 */
public class EntangledDockBlock extends BaseEntityBlock {

    public static final MapCodec<EntangledDockBlock> CODEC = simpleCodec(EntangledDockBlock::new);

    /** The model's steps: base, column and cradle. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(4, 3, 4, 12, 9, 12),
            Block.box(2, 9, 2, 14, 11, 14));

    public EntangledDockBlock(Properties properties) {
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!EntangledDockBlockEntity.canBind(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EntangledDockBlockEntity dock) {
            dock.bind(player, stack);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.4f);
            player.sendOverlayMessage(Component.translatable("block.quantimium.entangled_dock.bound", stack.getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof EntangledDockBlockEntity dock)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() && dock.isBound()) {
            dock.release();
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
            player.sendOverlayMessage(Component.translatable("block.quantimium.entangled_dock.released"));
            return InteractionResult.CONSUME;
        }
        player.sendOverlayMessage(status(dock));
        return InteractionResult.CONSUME;
    }

    private static Component status(EntangledDockBlockEntity dock) {
        return switch (dock.status()) {
            case EntangledDockBlockEntity.STATUS_CHARGING ->
                    Component.translatable("block.quantimium.entangled_dock.status.charging", dock.shown().getHoverName());
            case EntangledDockBlockEntity.STATUS_FULL ->
                    Component.translatable("block.quantimium.entangled_dock.status.full", dock.shown().getHoverName());
            case EntangledDockBlockEntity.STATUS_AWAY ->
                    Component.translatable("block.quantimium.entangled_dock.status.away", dock.shown().getHoverName());
            case EntangledDockBlockEntity.STATUS_NO_POWER ->
                    Component.translatable("block.quantimium.entangled_dock.status.power");
            default -> Component.translatable("block.quantimium.entangled_dock.status.unbound");
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EntangledDockBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.ENTANGLED_DOCK_BE.get(), EntangledDockBlockEntity::tick);
    }
}
