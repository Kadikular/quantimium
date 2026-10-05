package com.kadikular.quantimium.block;

import net.minecraft.world.level.LevelReader;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Fixed nether-sized frame: inner 2×3, outer 4×5. Corners are optional, same as vanilla.
 */
public final class StabilisedPortalShape {

    public static final int INNER_WIDTH = 2;
    public static final int INNER_HEIGHT = 3;

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction right;
    @Nullable
    private final BlockPos bottomLeft;
    private final int width;
    private final int height;
    private int portalBlocks;

    private StabilisedPortalShape(LevelAccessor level, BlockPos origin, Direction.Axis axis) {
        this.level = level;
        this.axis = axis;
        this.right = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        this.bottomLeft = findBottomLeft(origin);
        if (this.bottomLeft == null) {
            this.width = 0;
            this.height = 0;
        } else {
            this.width = measureWidth();
            this.height = this.width == INNER_WIDTH ? measureHeight() : 0;
        }
    }

    public static boolean tryLight(LevelAccessor level, BlockPos origin) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            StabilisedPortalShape shape = new StabilisedPortalShape(level, origin, axis);
            if (shape.isEmptyFrame()) {
                shape.fill();
                return true;
            }
        }
        return false;
    }

    public static boolean isComplete(LevelReader level, BlockPos origin, Direction.Axis axis) {
        // Only ever asked about a live level, which is a LevelAccessor; anything else has no frame to find.
        return level instanceof LevelAccessor accessor
                && new StabilisedPortalShape(accessor, origin, axis).isFilledFrame();
    }

    private boolean isEmptyFrame() {
        return isValid() && portalBlocks == 0;
    }

    private boolean isFilledFrame() {
        return isValid() && portalBlocks == INNER_WIDTH * INNER_HEIGHT;
    }

    private boolean isValid() {
        return bottomLeft != null && width == INNER_WIDTH && height == INNER_HEIGHT;
    }

    private void fill() {
        BlockState portal = ModBlocks.STABILISED_PORTAL.get().defaultBlockState()
                .setValue(StabilisedPortalBlock.AXIS, axis);
        BlockPos.betweenClosed(bottomLeft,
                        bottomLeft.relative(Direction.UP, height - 1).relative(right, width - 1))
                .forEach(pos -> level.setBlock(pos, portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
    }

    @Nullable
    private BlockPos findBottomLeft(BlockPos pos) {
        int floor = Math.max(level.getMinY(), pos.getY() - INNER_HEIGHT);
        while (pos.getY() > floor && isInterior(level.getBlockState(pos.below()))) {
            pos = pos.below();
        }
        Direction left = right.getOpposite();
        int steps = distanceToFrame(pos, left) - 1;
        return steps < 0 ? null : pos.relative(left, steps);
    }

    private int measureWidth() {
        int found = distanceToFrame(bottomLeft, right);
        return found == INNER_WIDTH ? found : 0;
    }

    private int distanceToFrame(BlockPos start, Direction direction) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= INNER_WIDTH; i++) {
            cursor.set(start).move(direction, i);
            BlockState here = level.getBlockState(cursor);
            if (!isInterior(here)) {
                return isFrame(here) ? i : 0;
            }
            if (!isFrame(level.getBlockState(cursor.move(Direction.DOWN)))) {
                return 0;
            }
        }
        return 0;
    }

    private int measureHeight() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < INNER_HEIGHT; y++) {
            cursor.set(bottomLeft).move(Direction.UP, y).move(right, -1);
            if (!isFrame(level.getBlockState(cursor))) return 0;
            cursor.set(bottomLeft).move(Direction.UP, y).move(right, width);
            if (!isFrame(level.getBlockState(cursor))) return 0;
            for (int x = 0; x < width; x++) {
                cursor.set(bottomLeft).move(Direction.UP, y).move(right, x);
                BlockState here = level.getBlockState(cursor);
                if (!isInterior(here)) return 0;
                if (here.is(ModBlocks.STABILISED_PORTAL.get())) portalBlocks++;
            }
        }
        for (int x = 0; x < width; x++) {
            cursor.set(bottomLeft).move(Direction.UP, INNER_HEIGHT).move(right, x);
            if (!isFrame(level.getBlockState(cursor))) return 0;
        }
        return INNER_HEIGHT;
    }

    private static boolean isFrame(BlockState state) {
        return state.is(ModBlocks.STABILISED_PORTAL_FRAME.get());
    }

    private static boolean isInterior(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(ModBlocks.STABILISED_PORTAL.get());
    }
}
