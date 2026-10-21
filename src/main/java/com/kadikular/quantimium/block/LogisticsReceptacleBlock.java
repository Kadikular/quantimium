package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.BeamAnchor;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LogisticsMember;
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
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A Logistics Receptacle: a stand on the inventory it serves, its part on that face, and a focus in the middle that
 * light passes straight through, so one beam along a row of machines joins them all to a logistics network. Placed
 * against an inventory, it serves that one; a wrench, or a sneaking empty hand, on a face moves it there. An empty
 * hand opens it: storage, provider or requester, active or passive, its priority and its filter.
 */
public class LogisticsReceptacleBlock extends BaseEntityBlock implements BeamAnchor, com.kadikular.quantimium.item.BlockTooltip {
    public static final MapCodec<LogisticsReceptacleBlock> CODEC = simpleCodec(LogisticsReceptacleBlock::new);
    /** The face its part is on: towards the inventory it serves. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<LogisticsMember.Role> ROLE = EnumProperty.create("role", LogisticsMember.Role.class);
    /** Whether a controller's light reaches it. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Drawn for a stand on the floor (part facing down), turned to each face.
        for (Direction face : Direction.values()) {
            SHAPES.put(face, Shapes.or(turned(face, 2, 0, 2, 14, 2, 14), turned(face, 7, 2, 7, 9, 6, 9), turned(face, 6, 6, 6, 10, 10, 10)));
        }
    }

    /** A box drawn for a stand on the floor, turned so the floor is {@code face}. */
    private static VoxelShape turned(Direction face, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] a = place(face, x0, y0, z0);
        double[] b = place(face, x1, y1, z1);
        return Block.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }

    private static double[] place(Direction face, double x, double y, double z) {
        return switch (face) {
            case DOWN -> new double[] {x, y, z};
            case UP -> new double[] {x, 16 - y, z};
            case NORTH -> new double[] {x, z, y};
            case SOUTH -> new double[] {x, z, 16 - y};
            case WEST -> new double[] {y, x, z};
            case EAST -> new double[] {16 - y, x, z};
        };
    }

    public LogisticsReceptacleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(ROLE, LogisticsMember.Role.STORAGE).setValue(LIT, false));
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("block.quantimium.logistics_receptacle.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROLE, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public Vec3 beamPoint(BlockState state, BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModTags.TOOLS_WRENCH)) return LaserTools.isOne(stack) ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
        return point(state, level, pos, hit.getDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return point(state, level, pos, hit.getDirection());
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LogisticsReceptacleBlockEntity receptacle) {
            // Whether what it's on takes power, for the screen to offer powering it.
            receptacle.checkPower();
            player.openMenu(receptacle, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Moves its part to the face clicked. */
    private static InteractionResult point(BlockState state, Level level, BlockPos pos, Direction face) {
        if (!level.isClientSide() && state.getValue(FACING) != face) {
            level.setBlock(pos, state.setValue(FACING, face), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 1.2f);
            LaserNetworks.changed(level);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsReceptacleBlockEntity(pos, state);
    }
}
