package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A Logistics Controller: everything its light reaches is one logistics network. Placed, it shines towards you;
 * a wrench, or a sneaking empty hand, on another face adds an output there, or takes one away, as on an emitter.
 * An empty hand says how its network stands.
 */
public class LogisticsControllerBlock extends BaseEntityBlock implements com.kadikular.quantimium.item.BlockTooltip {
    public static final MapCodec<LogisticsControllerBlock> CODEC = simpleCodec(LogisticsControllerBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Which faces light leaves by, for the model. */
    public static final Map<Direction, BooleanProperty> OUTPUTS = net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;

    public LogisticsControllerBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false);
        for (BooleanProperty output : OUTPUTS.values()) state = state.setValue(output, false);
        registerDefaultState(state.setValue(OUTPUTS.get(Direction.NORTH), true));
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("block.quantimium.logistics_controller.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
        OUTPUTS.values().forEach(builder::add);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(OUTPUTS.get(Direction.NORTH), false).setValue(FACING, facing).setValue(OUTPUTS.get(facing), true);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModTags.TOOLS_WRENCH)) return LaserTools.isOne(stack) ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
        return toggle(level, pos, player, hit.getDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return toggle(level, pos, player, hit.getDirection());
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LogisticsControllerBlockEntity controller) {
            player.sendOverlayMessage(controller.status());
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult toggle(Level level, BlockPos pos, Player player, Direction face) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LogisticsControllerBlockEntity controller)) return InteractionResult.SUCCESS;
        if (face == controller.back()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.quantimium.logistics_controller.back")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else if (controller.toggleOutput(face)) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, controller.outputs().contains(face) ? 1.3f : 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsControllerBlockEntity(pos, state);
    }
}
