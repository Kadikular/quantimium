package com.kadikular.quantimium.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.LevelReader;
import com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SupportType;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rift Lens: a stone ring a Harvest Laser fires through. The beam tears a small hole in the world at
 * its middle, and through that hole it reaches the crystal on the other side. The ring does nothing
 * alone; the laser opens and closes it.
 *
 * <p>It grows a strut to whatever holds it: any solid face beside it in the ring's own plane, or
 * another ring turned the same way, so it stands on a floor, hangs from a ceiling, sits on a wall or
 * stacks with its neighbours.
 */
public class RiftLensBlock extends Block {

    public static final MapCodec<RiftLensBlock> CODEC = simpleCodec(RiftLensBlock::new);
    /** The way a beam passes through it. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** A laser is firing through it and the tear in its middle is open. */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    /** A strut to the block on that side. Only ever set across the axis. */
    public static final Map<Direction, BooleanProperty> STRUTS = PipeBlock.PROPERTY_BY_DIRECTION;

    /** What the lens is seated against at one end of its axis. Looks only: it works the same either way. */
    public enum Seat implements StringRepresentable {
        NONE("none"), GLASS("glass");

        private final String name;

        Seat(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Seated at the negative end of its axis (north, down or west): collared onto hall glass. */
    public static final EnumProperty<Seat> NEGATIVE = EnumProperty.create("negative", Seat.class);
    public static final EnumProperty<Seat> POSITIVE = EnumProperty.create("positive", Seat.class);

    public RiftLensBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(OPEN, false)
                .setValue(NEGATIVE, Seat.NONE).setValue(POSITIVE, Seat.NONE);
        for (BooleanProperty strut : STRUTS.values()) state = state.setValue(strut, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN, NEGATIVE, POSITIVE);
        STRUTS.values().forEach(builder::add);
    }

    /**
     * Turned along the line of a Harvest Laser aimed at this spot, if there is one. Placed against a
     * hall's glass, it faces into the glass, ready to seat on it. Otherwise along the way the player
     * faces, unless that would point it into the face it was placed on.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = laserAxis(context.getLevel(), context.getClickedPos());
        BlockPos against = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        if (axis == null && context.getLevel().getBlockState(against).is(ModBlocks.QUANTUM_ATTUNED_GLASS.get())) {
            axis = context.getClickedFace().getAxis();
        }
        if (axis == null) {
            axis = context.getHorizontalDirection().getAxis();
            if (axis == context.getClickedFace().getAxis()) axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        }
        return connect(defaultBlockState().setValue(AXIS, axis), context.getLevel(), context.getClickedPos());
    }

    /** The axis of a Harvest Laser within reach whose beam passes through {@code pos}, if any. */
    @Nullable
    private static Direction.Axis laserAxis(BlockGetter level, BlockPos pos) {
        for (Direction toward : Direction.values()) {
            for (int i = 1; i <= HarvestLaserBlockEntity.REACH; i++) {
                BlockState state = level.getBlockState(pos.relative(toward, i));
                if (state.is(ModBlocks.HARVEST_LASER.get()) && state.getValue(HarvestLaserBlock.FACING) == toward.getOpposite()) {
                    return toward.getAxis();
                }
                if (!state.isAir()) break;
            }
        }
        return null;
    }

    /** Turned to {@code axis}, with its struts worked out again. For a laser lining it up. */
    public static BlockState turnedTo(BlockState state, Direction.Axis axis, BlockGetter level, BlockPos pos) {
        return connect(state.setValue(AXIS, axis), level, pos);
    }

    /** Every strut and seat set for what's around it now. */
    private static BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.values()) state = refit(state, level, pos, side);
        return state;
    }

    /** The strut, or the seat, on {@code side}. */
    private static BlockState refit(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        state = state.setValue(STRUTS.get(side), holds(state, level, pos, side));
        if (side.getAxis() != state.getValue(AXIS)) return state;
        return state.setValue(side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? POSITIVE : NEGATIVE,
                seat(level, pos, side));
    }

    /** Hall glass right against the lens along its axis. */
    private static Seat seat(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockState(pos.relative(side)).is(ModBlocks.QUANTUM_ATTUNED_GLASS.get()) ? Seat.GLASS : Seat.NONE;
    }

    /** Whether the block on {@code side} holds the ring: across its axis, solid enough, or a ring the same way. */
    private static boolean holds(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (side.getAxis() == state.getValue(AXIS)) return false;
        BlockPos next = pos.relative(side);
        BlockState neighbour = level.getBlockState(next);
        if (neighbour.is(ModBlocks.RIFT_LENS.get())) return neighbour.getValue(AXIS) == state.getValue(AXIS);
        return neighbour.isFaceSturdy(level, next, side.getOpposite(), SupportType.CENTER);
    }

    /** Any wrench turns it to the next axis. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModTags.TOOLS_WRENCH)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) turn(level, pos, state);
        return InteractionResult.SUCCESS;
    }

    /** To the next axis: x, y, z, and round. */
    public static void turn(Level level, BlockPos pos, BlockState state) {
        Direction.Axis[] axes = Direction.Axis.values();
        Direction.Axis next = axes[(state.getValue(AXIS).ordinal() + 1) % axes.length];
        level.setBlock(pos, turnedTo(state, next, level, pos), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return refit(state, level, pos, direction);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(AXIS);
        BlockState turned = state;
        if (axis != Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            turned = turned.setValue(AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(STRUTS.get(rotation.rotate(side)), state.getValue(STRUTS.get(side)));
        }
        return turned;
    }

    /** The ring's frame, standing across the z axis (x, y, z to x, y, z in pixels): four bars. */
    private static final double[][] FRAME = {
            {4, 2, 6, 12, 4, 10}, {4, 12, 6, 12, 14, 10}, {2, 4, 6, 4, 12, 10}, {12, 4, 6, 14, 12, 10}};
    private static final Map<Direction.Axis, VoxelShape> FRAMES = new EnumMap<>(Direction.Axis.class);
    /** A strut from the frame's edge out to the face on each side; the same whichever way the ring is turned. */
    private static final Map<Direction, VoxelShape> STRUT_SHAPES = new EnumMap<>(Direction.class);
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    static {
        for (Direction.Axis axis : Direction.Axis.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : FRAME) shape = Shapes.or(shape, turned(box, axis));
            FRAMES.put(axis, shape);
        }
        STRUT_SHAPES.put(Direction.DOWN, Block.box(6, 0, 6, 10, 2, 10));
        STRUT_SHAPES.put(Direction.UP, Block.box(6, 14, 6, 10, 16, 10));
        STRUT_SHAPES.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 2));
        STRUT_SHAPES.put(Direction.SOUTH, Block.box(6, 6, 14, 10, 10, 16));
        STRUT_SHAPES.put(Direction.WEST, Block.box(0, 6, 6, 2, 10, 10));
        STRUT_SHAPES.put(Direction.EAST, Block.box(14, 6, 6, 16, 10, 10));
    }

    /** A box of the frame across z, turned as the blockstate turns the model for {@code axis}. */
    private static VoxelShape turned(double[] box, Direction.Axis axis) {
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        return switch (axis) {
            case Z -> Block.box(x0, y0, z0, x1, y1, z1);
            // y 90: (x, z) -> (16 - z, x)
            case X -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            // x 90: (y, z) -> (z, 16 - y)
            case Y -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state, key -> {
            VoxelShape shape = FRAMES.get(key.getValue(AXIS));
            for (Direction side : Direction.values()) {
                if (key.getValue(STRUTS.get(side))) shape = Shapes.or(shape, STRUT_SHAPES.get(side));
            }
            return shape;
        });
    }
}
