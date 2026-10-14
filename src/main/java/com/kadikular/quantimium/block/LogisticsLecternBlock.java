package com.kadikular.quantimium.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Logistics Lectern: a Logistics Terminal as a stand rather than a block. Light comes in to the focus on its post;
 * the screen over it tilts towards whoever placed it. Model: tools/laser_art.py logistics_lectern.
 */
public class LogisticsLecternBlock extends LogisticsTerminalBlock implements com.kadikular.quantimium.laser.BeamAnchor {
    public static final MapCodec<LogisticsLecternBlock> LECTERN_CODEC = simpleCodec(LogisticsLecternBlock::new);

    /** Drawn facing north: base, post, focus, neck, and the screen's box round its tilt. */
    private static final double[][] BOXES = {
            {2, 0, 2, 14, 2, 14}, {7, 2, 7, 9, 4, 9}, {6, 4, 6, 10, 8, 10}, {7, 8, 8, 9, 11, 10}, {2, 9, 3, 14, 14, 14}};
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : BOXES) {
                double[] from = turned(facing, b[0], b[2]);
                double[] to = turned(facing, b[3], b[5]);
                shape = Shapes.or(shape, Block.box(Math.min(from[0], to[0]), b[1], Math.min(from[1], to[1]),
                        Math.max(from[0], to[0]), b[4], Math.max(from[1], to[1])));
            }
            SHAPES.put(facing, shape);
        }
    }

    /** Where (x, z), drawn facing north, lands facing {@code facing}: as the blockstate turns the model. */
    private static double[] turned(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {16 - z, x};
            case SOUTH -> new double[] {16 - x, 16 - z};
            case WEST -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }

    public LogisticsLecternBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return LECTERN_CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Light meets it at its focus. */
    @Override
    public Vec3 beamPoint(BlockState state, BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 6 / 16.0, pos.getZ() + 0.5);
    }
}
