package com.kadikular.quantimium.fold;

import com.kadikular.quantimium.block.FoldCoreBlock;
import com.kadikular.quantimium.block.FoldRailBlock;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The box a Fold Chamber's frame marks out: pylons on its eight corners, rails along its twelve
 * edges, and the core in the middle of the bottom front edge. Each side is built to size on its own,
 * from {@link #MIN_SIZE} to {@link #MAX_SIZE}; the width is odd so the core sits on its centre line.
 *
 * <p>The frame is the volume. Every cell of the box that is not on an edge folds, faces included, so
 * a 7×7 Foundry, two tall, fits a 7 × 3 × 7 chamber standing one block up, clear of the bottom
 * layer's edges.
 *
 * <p>Laid out from the core: {@code facing} points out of the front face, the bottom front edge runs
 * along {@link #across()}, and the box reaches {@code depth - 1} back and {@code height - 1} up.
 */
public record FoldChamber(BlockPos core, Direction facing, int width, int height, int depth) {

    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 17;

    /** Half the width: how far the core is from either front pylon. */
    public int half() {
        return (width - 1) / 2;
    }

    /** Width by height by depth, as the tooltip and messages put it. */
    public String dimensions() {
        return dimensions(width, height, depth);
    }

    public static String dimensions(int width, int height, int depth) {
        return width + "×" + height + "×" + depth;
    }

    /** The box's extent east–west, whichever way the chamber faces. */
    public int sizeX() {
        return across().getAxis() == Direction.Axis.X ? width : depth;
    }

    /** The box's extent north–south, whichever way the chamber faces. */
    public int sizeZ() {
        return across().getAxis() == Direction.Axis.Z ? width : depth;
    }

    /** The box in world terms: east–west × height × north–south. */
    public String worldDimensions() {
        return dimensions(sizeX(), height, sizeZ());
    }

    /** The box's north-west bottom corner. */
    public BlockPos minCorner() {
        BlockPos a = at(-half(), 0, 0);
        BlockPos b = at(half(), 0, depth - 1);
        return new BlockPos(Math.min(a.getX(), b.getX()), core.getY(), Math.min(a.getZ(), b.getZ()));
    }

    /** The bottom front edge's direction, to the right as you face the chamber's front. */
    public Direction across() {
        return facing.getCounterClockWise();
    }

    public Direction back() {
        return facing.getOpposite();
    }

    /** The cell {@code lateral} along the front edge, {@code up} high and {@code back} from the core. */
    public BlockPos at(int lateral, int up, int back) {
        return core.relative(across(), lateral).above(up).relative(back(), back);
    }

    /** How many of the box's bounding planes the cell lies on: 0 inside, 1 on a face, 2 on an edge, 3 a corner. */
    private int boundaries(int lateral, int up, int back) {
        int on = 0;
        if (Math.abs(lateral) == half()) on++;
        if (up == 0 || up == height - 1) on++;
        if (back == 0 || back == depth - 1) on++;
        return on;
    }

    /** Every cell that folds: the whole box but its edges. */
    public void forEachVolumeCell(Consumer<BlockPos> action) {
        int h = half();
        for (int up = 0; up < height; up++) {
            for (int back = 0; back < depth; back++) {
                for (int lateral = -h; lateral <= h; lateral++) {
                    if (boundaries(lateral, up, back) < 2) action.accept(at(lateral, up, back));
                }
            }
        }
    }

    public List<BlockPos> volume() {
        List<BlockPos> cells = new ArrayList<>();
        forEachVolumeCell(cells::add);
        return cells;
    }

    /** True when {@code pos} is one of the cells that fold. */
    public boolean contains(BlockPos pos) {
        int[] local = local(pos);
        return local != null && boundaries(local[0], local[1], local[2]) < 2;
    }

    /** {@code pos} as (lateral, up, back), or null outside the box. */
    private int[] local(BlockPos pos) {
        BlockPos offset = pos.subtract(core);
        Direction across = across();
        Direction back = back();
        int lateral = offset.getX() * across.getStepX() + offset.getZ() * across.getStepZ();
        int behind = offset.getX() * back.getStepX() + offset.getZ() * back.getStepZ();
        int up = offset.getY();
        if (Math.abs(lateral) > half() || up < 0 || up >= height || behind < 0 || behind >= depth) return null;
        return new int[] {lateral, up, behind};
    }

    /** Every frame cell with what belongs there, core included. */
    public void forEachFrameCell(FrameVisitor visitor) {
        int h = half();
        for (int up = 0; up < height; up++) {
            for (int back = 0; back < depth; back++) {
                for (int lateral = -h; lateral <= h; lateral++) {
                    int on = boundaries(lateral, up, back);
                    if (on < 2) continue;
                    BlockPos pos = at(lateral, up, back);
                    if (on == 3) {
                        visitor.visit(pos, Part.PYLON, null);
                    } else if (lateral == 0 && up == 0 && back == 0) {
                        visitor.visit(pos, Part.CORE, null);
                    } else {
                        // The one coordinate not on a boundary is the one the edge runs along.
                        Direction.Axis axis = Math.abs(lateral) != h ? across().getAxis()
                                : (up != 0 && up != height - 1) ? Direction.Axis.Y
                                : back().getAxis();
                        visitor.visit(pos, Part.RAIL, axis);
                    }
                }
            }
        }
    }

    public enum Part { PYLON, RAIL, CORE }

    @FunctionalInterface
    public interface FrameVisitor {
        void visit(BlockPos pos, Part part, Direction.Axis railAxis);
    }

    /** A chamber found from its core, or why there isn't one. */
    public record Detection(FoldChamber chamber, Component problem) {
        public boolean found() {
            return chamber != null;
        }
    }

    /**
     * Reads the frame around the core at {@code core}. The bottom front edge sets the width: rails out
     * to a pylon on each side, the same distance both ways. From the right-hand front pylon, the rails
     * up and back set the height and depth. Then every other edge must match.
     */
    public static Detection detect(Level level, BlockPos core) {
        BlockState coreState = level.getBlockState(core);
        if (!coreState.is(ModBlocks.FOLD_CORE.get())) {
            return new Detection(null, Component.translatable("message.quantimium.fold.no_core"));
        }
        Direction facing = coreState.getValue(FoldCoreBlock.FACING);
        Direction across = facing.getCounterClockWise();
        int right = reachPylon(level, core, across);
        int left = reachPylon(level, core, across.getOpposite());
        if (right < 0 || left < 0 || right != left) {
            return new Detection(null, Component.translatable("message.quantimium.fold.front_edge"));
        }
        BlockPos corner = core.relative(across, right);
        int up = reachPylon(level, corner, Direction.UP);
        int back = reachPylon(level, corner, facing.getOpposite());
        if (up < 0 || back < 0) {
            return new Detection(null, Component.translatable("message.quantimium.fold.corner_edges"));
        }
        int width = 2 * right + 1;
        int height = up + 1;
        int depth = back + 1;
        if (width < MIN_SIZE || width > MAX_SIZE || height < MIN_SIZE || height > MAX_SIZE
                || depth < MIN_SIZE || depth > MAX_SIZE) {
            return new Detection(null, Component.translatable("message.quantimium.fold.size", MIN_SIZE, MAX_SIZE));
        }
        FoldChamber chamber = new FoldChamber(core.immutable(), facing, width, height, depth);
        BlockPos[] wrong = new BlockPos[1];
        chamber.forEachFrameCell((pos, part, axis) -> {
            if (wrong[0] == null && !chamber.fits(level, pos, part, axis)) wrong[0] = pos.immutable();
        });
        if (wrong[0] != null) {
            BlockPos at = wrong[0];
            BlockState there = level.getBlockState(at);
            // Most often the machine is too big for the frame and pokes into an edge.
            if (!there.isAir() && !there.is(ModBlocks.FOLD_RAIL.get()) && !there.is(ModBlocks.FOLD_PYLON.get())) {
                return new Detection(null, Component.translatable("message.quantimium.fold.frame_blocked",
                        there.getBlock().getName(), at.getX(), at.getY(), at.getZ()));
            }
            return new Detection(null, Component.translatable("message.quantimium.fold.frame_gap",
                    at.getX(), at.getY(), at.getZ()));
        }
        return new Detection(chamber, null);
    }

    private boolean fits(Level level, BlockPos pos, Part part, Direction.Axis axis) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return switch (part) {
            case PYLON -> state.is(ModBlocks.FOLD_PYLON.get());
            case CORE -> pos.equals(core);
            case RAIL -> state.is(ModBlocks.FOLD_RAIL.get()) && state.getValue(FoldRailBlock.AXIS) == axis;
        };
    }

    /**
     * Rails from {@code from} to the first pylon along {@code direction}: the pylon's distance, or -1.
     * A pylon right next to it counts, so a side can be as short as the frame allows.
     */
    private static int reachPylon(Level level, BlockPos from, Direction direction) {
        for (int step = 1; step < MAX_SIZE; step++) {
            BlockPos pos = from.relative(direction, step);
            if (!level.isLoaded(pos)) return -1;
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.FOLD_PYLON.get())) return step;
            if (!state.is(ModBlocks.FOLD_RAIL.get()) || state.getValue(FoldRailBlock.AXIS) != direction.getAxis()) {
                return -1;
            }
        }
        return -1;
    }
}
