package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.SplitterBlockEntity;
import com.kadikular.quantimium.init.ModTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Splitter: light out of every face you set. A wrench, or a sneaking empty hand, on a face adds or
 * takes away an output there; an empty hand steps through how it listens to redstone.
 */
public class SplitterBlock extends LaserMirrorBlock {
    public static final MapCodec<SplitterBlock> SPLITTER_CODEC = simpleCodec(SplitterBlock::new);
    /** Whether light leaves by that face: what the block shows. */
    public static final java.util.Map<Direction, net.minecraft.world.level.block.state.properties.BooleanProperty> OUTPUTS =
            net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;

    public SplitterBlock(Properties properties) {
        super(properties);
        BlockState state = defaultBlockState();
        for (var property : OUTPUTS.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        OUTPUTS.values().forEach(builder::add);
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(OUTPUTS.get(state.getValue(FACING)), true);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return SPLITTER_CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModTags.TOOLS_WRENCH)) return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        return toggle(level, pos, hit.getDirection(), player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return toggle(level, pos, hit.getDirection(), player);
        return cycleMode(level, pos, player);
    }

    private static InteractionResult toggle(Level level, BlockPos pos, Direction face, @org.jetbrains.annotations.Nullable Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SplitterBlockEntity splitter && splitter.toggleOutput(face)) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f,
                    splitter.outputs().contains(face) ? 1.3f : 0.8f);
            if (player != null) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.quantimium.splitter.outputs",
                        String.join(", ", splitter.outputs().stream().map(Direction::getSerializedName).toList()))
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SplitterBlockEntity(pos, state);
    }
}
