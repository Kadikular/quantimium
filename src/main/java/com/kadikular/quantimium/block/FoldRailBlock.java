package com.kadikular.quantimium.block;

import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * An edge of a Fold Chamber: a tube of attuned glass that turns to its edge like a pillar.
 *
 * <p>Rails in a row read as one tube. A collar closes an end only where the run stops, which in a
 * whole frame is where it keys into a pylon or the core; those two ends are the blockstate's
 * {@link #CAP_NEGATIVE} and {@link #CAP_POSITIVE}, kept up to date from the neighbours.
 */
public class FoldRailBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** A collar on the end towards the axis's negative direction. */
    public static final BooleanProperty CAP_NEGATIVE = BooleanProperty.create("cap_negative");
    public static final BooleanProperty CAP_POSITIVE = BooleanProperty.create("cap_positive");

    private static final VoxelShape SHAPE_Y = box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape SHAPE_X = box(0, 4, 4, 16, 12, 12);
    private static final VoxelShape SHAPE_Z = box(4, 4, 0, 12, 12, 16);

    public FoldRailBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(CAP_NEGATIVE, true)
                .setValue(CAP_POSITIVE, true)
                .setValue(QuantumFoundryStructure.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, CAP_NEGATIVE, CAP_POSITIVE, QuantumFoundryStructure.FORMED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction.Axis axis = edgeAxis(level, pos, context.getClickedFace().getAxis());
        return capped(defaultBlockState().setValue(AXIS, axis), level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour,
                                     RandomSource random) {
        if (direction.getAxis() != state.getValue(AXIS)) return state;
        return capped(state, level, pos);
    }

    private BlockState capped(BlockState state, BlockGetter level, BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);
        return state
                .setValue(CAP_NEGATIVE, !continues(level, pos, Direction.fromAxisAndDirection(axis,
                        Direction.AxisDirection.NEGATIVE)))
                .setValue(CAP_POSITIVE, !continues(level, pos, Direction.fromAxisAndDirection(axis,
                        Direction.AxisDirection.POSITIVE)));
    }

    /** Whether the tube runs on into the next block, so this end needs no collar. */
    private boolean continues(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState next = level.getBlockState(pos.relative(direction));
        return next.is(this) && next.getValue(AXIS) == direction.getAxis();
    }

    /**
     * The axis with frame on both ends, else on one, else the face clicked. Building a frame you
     * usually click the side of the last rail, which would turn the next one crosswise.
     */
    private static Direction.Axis edgeAxis(BlockGetter level, BlockPos pos, Direction.Axis fallback) {
        Direction.Axis best = fallback;
        int bestWeight = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            int weight = frameWeight(level, pos, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE), axis)
                    + frameWeight(level, pos, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), axis);
            if (weight > bestWeight) {
                best = axis;
                bestWeight = weight;
            }
        }
        return best;
    }

    private static int frameWeight(BlockGetter level, BlockPos pos, Direction direction, Direction.Axis axis) {
        BlockState next = level.getBlockState(pos.relative(direction));
        if (next.is(ModBlocks.FOLD_PYLON.get())) return 1;
        if (next.is(ModBlocks.FOLD_CORE.get())) {
            return next.getValue(FoldCoreBlock.FACING).getCounterClockWise().getAxis() == axis ? 1 : 0;
        }
        if (next.is(ModBlocks.FOLD_RAIL.get())) return next.getValue(AXIS) == axis ? 1 : 0;
        return 0;
    }
}
