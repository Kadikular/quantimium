package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.SolarEmitterBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserTuning;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A Solar Emitter: a glass plate on four corner posts, gathering sunlight into a small focus and sending it
 * out of a simple emitter on one side as light, {@link LaserTuning#SOLAR_FE} FE/t in full sun. It holds
 * nothing. Light from other Solar Emitters shining into it joins its own, up to a chain's worth. A sneaking
 * click on a side, by hand or with a wrench, sends its light out there; never up, through its glass.
 */
public class SolarEmitterBlock extends BaseEntityBlock implements com.kadikular.quantimium.item.BlockTooltip {
    public static final MapCodec<SolarEmitterBlock> CODEC = simpleCodec(SolarEmitterBlock::new);
    public static final EnumProperty<Direction> OUTPUT = EnumProperty.create("output", Direction.class, direction -> direction != Direction.UP);
    /** Whether the sky gives it any light: its funnel of light shows. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty DAY =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("day");

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public SolarEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OUTPUT, Direction.NORTH).setValue(DAY, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTPUT, DAY);
    }

    /** Shining the way you face, so a row placed walking forward chains on. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(OUTPUT, context.getHorizontalDirection());
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(OUTPUT, rotation.rotate(state.getValue(OUTPUT)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(OUTPUT, mirror.mirror(state.getValue(OUTPUT)));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModTags.TOOLS_WRENCH)) return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        return player.isSecondaryUseActive() ? point(state, level, pos, player, hit.getDirection()) : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return player.isSecondaryUseActive() ? point(state, level, pos, player, hit.getDirection()) : InteractionResult.PASS;
    }

    /** Sends its light out of the side clicked: not up, through its glass. */
    private static InteractionResult point(BlockState state, Level level, BlockPos pos, Player player, Direction face) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (face == Direction.UP) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.solar_emitter.up").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(OUTPUT) != face) {
            level.setBlock(pos, state.setValue(OUTPUT, face), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 1.2f);
            LaserNetworks.changed(level);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.SOLAR_EMITTER_BE.get(), SolarEmitterBlockEntity::serverTick);
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("block.quantimium.solar_emitter.tip", LaserTuning.SOLAR_FE, LaserTuning.SOLAR_CHAIN_MAX)
                .withStyle(ChatFormatting.GRAY));
    }
}
