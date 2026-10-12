package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserRelayBlockEntity;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.LaserNetworks;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A Laser Relay: whatever light reaches it, from any side, leaves by every face it's set to, refocused, shared by
 * what each beam's blocks ask for. Placed, its one output faces you; a wrench, or a sneaking empty hand, on a face
 * adds or takes away an output there (one always stays). An empty hand steps through how it listens to redstone.
 */
public class LaserRelayBlock extends BaseEntityBlock {
    public static final MapCodec<LaserRelayBlock> CODEC = simpleCodec(LaserRelayBlock::new);
    /** The face it was placed facing: its first output. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** Whether light leaves by that face: what the block shows. */
    public static final java.util.Map<Direction, net.minecraft.world.level.block.state.properties.BooleanProperty> OUTPUTS =
            net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;

    public LaserRelayBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (var property : OUTPUTS.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        OUTPUTS.values().forEach(builder::add);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(OUTPUTS.get(facing), true);
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
        // Anything else in hand (the Linker, the Spectroscope) does its own thing; an empty hand uses the block.
        if (!stack.is(ModTags.TOOLS_WRENCH)) return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        return toggle(level, pos, hit.getDirection(), player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return toggle(level, pos, hit.getDirection(), player);
        return cycleMode(level, pos, player);
    }

    /** An empty hand steps through how it listens to redstone: ignored, with a signal, without one, pulse. */
    protected static InteractionResult cycleMode(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserRelayBlockEntity mirror) {
            mirror.cycleMode();
            player.sendOverlayMessage(mirror.mode().label().copy().withStyle(net.minecraft.ChatFormatting.AQUA));
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6f, 1.0f + 0.1f * mirror.mode().ordinal());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserRelayBlockEntity mirror) mirror.onSignal(level.hasNeighborSignal(pos));
    }

    /** Adds an output on the face clicked, or takes it away: one always stays. */
    private static InteractionResult toggle(Level level, BlockPos pos, Direction face, @Nullable Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserRelayBlockEntity relay && relay.toggleOutput(face)) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, relay.outputs().contains(face) ? 1.3f : 0.8f);
            if (player != null) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.quantimium.laser_relay.outputs",
                        String.join(", ", relay.outputs().stream().map(Direction::getSerializedName).toList()))
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserRelayBlockEntity(pos, state);
    }
}
