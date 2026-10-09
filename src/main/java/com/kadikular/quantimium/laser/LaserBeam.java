package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One straight run of light: out of a node's face, or from a prism at a block it's linked to, along
 * any line. How far it got and what stopped it.
 *
 * @param face   the way it travels, to the nearest of the six: which face of what it ends on it reaches
 * @param aim    the block a prism aimed it at, or null for light leaving a face
 * @param length blocks crossed before what it ends on
 * @param hit    the block it ends on, or null if it faded in open air at its range
 * @param end    what that block is to the network
 * @param flow   FE a tick it carried when last worked out, for its brightness
 * @param live   whether light runs along it at all: a powered, switched-on source reaches it, even when
 *               nothing at its end needs power, so it glows dimly as it checks
 * @param lenses the lenses it's met, packed as {@link Lens} says: what it does besides carry power
 */
public record LaserBeam(BlockPos from, Direction face, @Nullable BlockPos aim, int length, @Nullable BlockPos hit, End end,
                        long flow, boolean live, int lenses) {

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
        return new LaserBeam(from, face, aim, length, hit, end, flow, live, lenses);
    }

    public LaserBeam withLive(boolean live) {
        return new LaserBeam(from, face, aim, length, hit, end, flow, live, lenses);
    }

    public LaserBeam withLenses(int lenses) {
        return new LaserBeam(from, face, aim, length, hit, end, flow, live, lenses);
    }

    /** Whether it's the same run of light as {@code other}, whatever each carried. */
    public boolean sameRun(LaserBeam other) {
        return face == other.face && length == other.length && end == other.end && Objects.equals(aim, other.aim)
                && Objects.equals(hit, other.hit);
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
            LaserBeam ended = endsAt(level, from, direction, null, step, at.immutable());
            if (ended != null) return ended;
        }
        return new LaserBeam(from, direction, null, range, null, End.OPEN, 0, false, Lens.NONE);
    }

    /**
     * Follows light from the middle of {@code from} towards the middle of {@code target}, through every
     * block the line crosses, for up to {@code range} blocks. Ends on the first block that stops light,
     * or on the target.
     */
    public static LaserBeam traceTo(Level level, BlockPos from, BlockPos target, int range) {
        Vec3 start = from.getCenter();
        Vec3 delta = target.getCenter().subtract(start);
        Direction face = Direction.getApproximateNearest(delta.x, delta.y, delta.z);
        if (delta.length() > range) return new LaserBeam(from, face, target, range, null, End.OPEN, 0, false, Lens.NONE);
        // Every block the line passes through, in order (Amanatides and Woo's walk).
        int x = from.getX(), y = from.getY(), z = from.getZ();
        int stepX = (int) Math.signum(delta.x), stepY = (int) Math.signum(delta.y), stepZ = (int) Math.signum(delta.z);
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.x);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.y);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.z);
        // From the middle of a block, the first boundary is half a block away along each axis.
        double tMaxX = tDeltaX * 0.5, tMaxY = tDeltaY * 0.5, tMaxZ = tDeltaZ * 0.5;
        int steps = 0;
        while (steps++ < range * 3 + 3) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            BlockPos pos = new BlockPos(x, y, z);
            int length = (int) Math.round(pos.getCenter().distanceTo(start)) - 1;
            if (pos.equals(target)) {
                LaserBeam ended = endsAt(level, from, face, target, length, pos);
                return ended != null ? ended : new LaserBeam(from, face, target, length, pos, End.BLOCKED, 0, false, Lens.NONE);
            }
            LaserBeam ended = endsAt(level, from, face, target, length, pos);
            if (ended != null) return ended;
        }
        return new LaserBeam(from, face, target, range, null, End.OPEN, 0, false, Lens.NONE);
    }

    /** What the light does at {@code pos}: ends there (and how), or null to go on through. */
    @Nullable
    private static LaserBeam endsAt(Level level, BlockPos from, Direction face, @Nullable BlockPos aim, int length, BlockPos pos) {
        if (!level.isLoaded(pos)) return new LaserBeam(from, face, aim, length, null, End.OPEN, 0, false, Lens.NONE);
        if (level.getBlockEntity(pos) instanceof LaserNode) return new LaserBeam(from, face, aim, length, pos, End.NODE, 0, false, Lens.NONE);
        BlockState state = level.getBlockState(pos);
        if (passes(level, pos, state)) return null;
        boolean takes = level.getCapability(Capabilities.Energy.BLOCK, pos, face.getOpposite()) != null;
        return new LaserBeam(from, face, aim, length, pos, takes ? End.SINK : End.BLOCKED, 0, false, Lens.NONE);
    }

    /** Whether light goes through this block. */
    public static boolean passes(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) return true;
        if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) return true;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) return false;
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** Traces this beam again the same way: light from a face, or aimed at a block. */
    public LaserBeam retrace(Level level, int range) {
        return aim == null ? trace(level, from, face, range) : traceTo(level, from, aim, range);
    }

    /** Whether this beam still runs as it did: every block it crosses still lets light through, and it ends on the same thing. */
    public boolean stillHolds(Level level, int range) {
        return sameRun(retrace(level, range));
    }

    /** Where the light starts and stops, block-world coordinates: from the node's side to what it ends on. */
    public Vec3[] ends() {
        Vec3 centre = from.getCenter();
        if (aim == null) {
            Vec3 dir = Vec3.atLowerCornerOf(face.getUnitVec3i());
            return new Vec3[] {centre.add(dir.scale(0.5)), centre.add(dir.scale(length + 0.5))};
        }
        Vec3 dir = aim.getCenter().subtract(centre).normalize();
        // Faded short of what it aimed at, it stops at its range; past it, at the middle of what it aimed at.
        double reach = hit == null ? Math.min(length + 0.5, aim.getCenter().distanceTo(centre))
                : Math.max(0.5, hit.getCenter().distanceTo(centre) - 0.5);
        return new Vec3[] {centre.add(dir.scale(0.5)), centre.add(dir.scale(reach))};
    }

    /** For the client: how it runs, what it ends on and how bright. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Dir", face.get3DDataValue());
        tag.putInt("Length", length);
        tag.putInt("End", end.ordinal());
        tag.putLong("Flow", flow);
        if (aim != null) tag.putLong("Aim", aim.asLong());
        if (hit != null) tag.putLong("Hit", hit.asLong());
        tag.putBoolean("Live", live);
        if (lenses != Lens.NONE) tag.putInt("Lenses", lenses);
        return tag;
    }

    public static LaserBeam load(BlockPos from, CompoundTag tag) {
        Direction face = Direction.from3DDataValue(tag.getIntOr("Dir", 0));
        int length = tag.getIntOr("Length", 0);
        End end = End.values()[Math.clamp(tag.getIntOr("End", 0), 0, End.values().length - 1)];
        BlockPos aim = tag.getLong("Aim").map(BlockPos::of).orElse(null);
        BlockPos hit = tag.getLong("Hit").map(BlockPos::of).orElse(null);
        return new LaserBeam(from, face, aim, length, hit, end, tag.getLongOr("Flow", 0), tag.getBooleanOr("Live", false),
                tag.getIntOr("Lenses", Lens.NONE));
    }
}
