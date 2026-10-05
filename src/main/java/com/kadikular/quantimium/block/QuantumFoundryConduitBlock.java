package com.kadikular.quantimium.block;

import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Flat floor rail that carries the field from a pillar to the plinth. The axis is a guess at
 * placement and is corrected by the controller once it knows which arm this conduit belongs to,
 * so a rail never sits crosswise to its own arm.
 */
public class QuantumFoundryConduitBlock extends QuantumFoundryStructurePartBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private static final VoxelShape SHAPE_Z = Shapes.or(
            box(3.0, 0.0, 0.0, 13.0, 3.0, 16.0),
            box(6.0, 3.0, 0.0, 10.0, 3.5, 16.0));
    private static final VoxelShape SHAPE_X = Shapes.or(
            box(0.0, 0.0, 3.0, 16.0, 3.0, 13.0),
            box(0.0, 3.0, 6.0, 16.0, 3.5, 10.0));

    public QuantumFoundryConduitBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis fallback = context.getHorizontalDirection().getAxis();
        return defaultBlockState().setValue(AXIS,
                armAxis(context.getLevel(), context.getClickedPos(), fallback));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    /** The axis with foundry blocks on both ends, falling back when the arm is still ambiguous. */
    private static Direction.Axis armAxis(BlockGetter level, BlockPos pos, Direction.Axis fallback) {
        int alongX = armWeight(level, pos, Direction.WEST) + armWeight(level, pos, Direction.EAST);
        int alongZ = armWeight(level, pos, Direction.NORTH) + armWeight(level, pos, Direction.SOUTH);
        if (alongX > alongZ) return Direction.Axis.X;
        if (alongZ > alongX) return Direction.Axis.Z;
        return fallback;
    }

    private static int armWeight(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        return neighbour.is(ModBlocks.QUANTUM_FOUNDRY_PLINTH.get())
                || neighbour.is(ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get())
                || neighbour.is(ModBlocks.QUANTUM_FOUNDRY_PILLAR.get()) ? 1 : 0;
    }
}
