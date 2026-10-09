package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserEmitterBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.LaserTuning;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A laser emitter, of one tier. Placed, it faces you. A wrench, or a sneaking
 * empty hand, on another face adds an output there, or takes one away; an empty hand opens it.
 */
public class LaserEmitterBlock extends BaseEntityBlock implements com.kadikular.quantimium.item.BlockTooltip {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final LaserTuning.Tier tier;

    public LaserEmitterBlock(Properties properties, LaserTuning.Tier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public LaserTuning.Tier tier() {
        return tier;
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip) {
        String rate = String.format(java.util.Locale.ROOT, "%,d", tier.rate);
        tooltip.accept(net.minecraft.network.chat.Component.translatable("block.quantimium.laser_emitter.tip", rate, tier.range)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (tier == LaserTuning.Tier.FLASH) {
            tooltip.accept(net.minecraft.network.chat.Component.translatable("block.quantimium.flash_emitter.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new LaserEmitterBlock(properties, tier));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        // The laser tools (the Linker, the Spectroscope) do their own thing; anything else opens it.
        if (!stack.is(ModTags.TOOLS_WRENCH)) return LaserTools.isOne(stack) ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
        return toggle(level, pos, hit.getDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return toggle(level, pos, hit.getDirection());
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter) {
            player.openMenu(emitter, pos);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult toggle(Level level, BlockPos pos, Direction face) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter
                && emitter.toggleOutput(face)) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f,
                    emitter.outputs().contains(face) ? 1.3f : 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.LASER_EMITTER_BE.get(), LaserEmitterBlockEntity::serverTick);
    }
}
