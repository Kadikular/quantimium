package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * One straight run of light from a node's face: how far it got and what stopped it.
 *
 * @param length blocks of open space crossed before what it ends on (0 if that's the very next block)
 * @param hit    the block it ends on, or null if it faded in open air at its range
 * @param end    what that block is to the network
 * @param flow   FE a tick it carried when last worked out, for its brightness
 */
public record LaserBeam(BlockPos from, Direction direction, int length, @Nullable BlockPos hit, End end, long flow) {

    /** What a beam ends on. */
    public enum End {
        /** Another laser node: the beam joins them. */
        NODE,
        /** A block that takes power on that face: the beam charges it. */
        SINK,
        /** A block that stops light and takes nothing. */
        BLOCKED,
        /** Nothing within range: it faded out. */
        OPEN
    }

    public LaserBeam withFlow(long flow) {
        return new LaserBeam(from, direction, length, hit, end, flow);
    }

    /**
     * Follows light from {@code from}'s {@code direction} face for up to {@code range} blocks. Light
     * crosses air, glass and anything with no collision (grass, torches, flowers); it ends on the first
     * other block, or on a laser node, or where a chunk isn't loaded.
     */
    public static LaserBeam trace(Level level, BlockPos from, Direction direction, int range) {
        BlockPos.MutableBlockPos at = from.mutable();
        for (int step = 0; step < range; step++) {
            at.move(direction);
            if (!level.isLoaded(at)) return new LaserBeam(from, direction, step, null, End.OPEN, 0);
            BlockPos pos = at.immutable();
            if (level.getBlockEntity(pos) instanceof LaserNode) return new LaserBeam(from, direction, step, pos, End.NODE, 0);
            BlockState state = level.getBlockState(pos);
            if (passes(level, pos, state)) continue;
            boolean takes = level.getCapability(Capabilities.Energy.BLOCK, pos, direction.getOpposite()) != null;
            return new LaserBeam(from, direction, step, pos, takes ? End.SINK : End.BLOCKED, 0);
        }
        return new LaserBeam(from, direction, range, null, End.OPEN, 0);
    }

    /** Whether light goes through this block. */
    public static boolean passes(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) return true;
        if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) return true;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) return false;
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** Whether this beam still runs as it did: every block it crosses still lets light through, and it ends on the same thing. */
    public boolean stillHolds(Level level, int range) {
        LaserBeam now = trace(level, from, direction, range);
        return now.length == length && now.end == end && java.util.Objects.equals(now.hit, hit);
    }

    /** For the client: direction, length, what it ends on and how bright. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Dir", direction.get3DDataValue());
        tag.putInt("Length", length);
        tag.putInt("End", end.ordinal());
        tag.putLong("Flow", flow);
        return tag;
    }

    public static LaserBeam load(BlockPos from, CompoundTag tag) {
        Direction direction = Direction.from3DDataValue(tag.getIntOr("Dir", 0));
        int length = tag.getIntOr("Length", 0);
        End end = End.values()[Math.clamp(tag.getIntOr("End", 0), 0, End.values().length - 1)];
        BlockPos hit = end == End.OPEN ? null : from.relative(direction, length + 1);
        return new LaserBeam(from, direction, length, hit, end, tag.getLongOr("Flow", 0));
    }
}
