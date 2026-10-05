package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Decoherence Projector (placeholder name): the Lance's beam on a pedestal. Powered, it watches the
 * mirror for the Veiled, mites and rift anchors and holds a beam on the most pressing. The real
 * world sees only the block light up and its orb brighten; the beam itself is drawn for the mirror.
 */
public class DecoherenceProjectorBlock extends BaseEntityBlock {

    public static final MapCodec<DecoherenceProjectorBlock> CODEC = simpleCodec(DecoherenceProjectorBlock::new);
    /** Beaming at something. The only part of its work the real world can see. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** The way it points out from what it's mounted on: up on a floor, down from a ceiling, out of a wall. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public DecoherenceProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(FACING, Direction.UP));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** A Cell, or a bound Tesseract to draw Cells through, goes in; sneaking with an empty hand takes it out. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof DecoherenceProjectorBlockEntity projector)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!projector.getCells().isItemValid(0, stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack left = projector.getCells().insertItem(0, player.getAbilities().instabuild ? stack.copy() : stack, false);
        if (!player.getAbilities().instabuild) player.setItemInHand(hand, left);
        return InteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof DecoherenceProjectorBlockEntity projector)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack taken = projector.getCells().extractItem(0, 64, false);
            if (!taken.isEmpty() && !player.addItem(taken)) player.drop(taken, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING);
    }

    /** The model's pedestal, standing up: base slab, column, capital; turned for each way it can point. */
    private static final double[][] PEDESTAL = {{0, 0, 0, 16, 3, 16}, {3, 3, 3, 13, 12, 13}, {1, 12, 1, 15, 16, 15}};
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : PEDESTAL) shape = Shapes.or(shape, turned(box, facing));
            SHAPES.put(facing, shape);
        }
    }

    /** A box of the upright pedestal (x, y, z to x, y, z in pixels), turned so its top points {@code facing}. */
    private static VoxelShape turned(double[] box, Direction facing) {
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        return switch (facing) {
            case UP -> Block.box(x0, y0, z0, x1, y1, z1);
            case DOWN -> Block.box(x0, 16 - y1, z0, x1, 16 - y0, z1);
            case NORTH -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case SOUTH -> Block.box(x0, z0, y0, x1, z1, y1);
            case WEST -> Block.box(16 - y1, x0, z0, 16 - y0, x1, z1);
            case EAST -> Block.box(y0, x0, z0, y1, x1, z1);
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecoherenceProjectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntities.DECOHERENCE_PROJECTOR_BE.get(), DecoherenceProjectorBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.DECOHERENCE_PROJECTOR_BE.get(), DecoherenceProjectorBlockEntity::serverTick);
    }
}
