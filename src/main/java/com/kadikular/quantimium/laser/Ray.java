package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the 26 ways light travels: a step of -1, 0 or 1 along each axis, not all 0. The 6 straight ways (a face's),
 * the 12 flat diagonals (45° in one plane) and the 8 corner diagonals (45° in two planes at once). Light runs along
 * these from block centre to block centre, so a beam reaching a block always passes through its middle.
 */
public enum Ray {
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0),
    DOWN_NORTH(0, -1, -1), DOWN_SOUTH(0, -1, 1), DOWN_WEST(-1, -1, 0), DOWN_EAST(1, -1, 0),
    UP_NORTH(0, 1, -1), UP_SOUTH(0, 1, 1), UP_WEST(-1, 1, 0), UP_EAST(1, 1, 0),
    NORTH_WEST(-1, 0, -1), NORTH_EAST(1, 0, -1), SOUTH_WEST(-1, 0, 1), SOUTH_EAST(1, 0, 1),
    DOWN_NORTH_WEST(-1, -1, -1), DOWN_NORTH_EAST(1, -1, -1), DOWN_SOUTH_WEST(-1, -1, 1), DOWN_SOUTH_EAST(1, -1, 1),
    UP_NORTH_WEST(-1, 1, -1), UP_NORTH_EAST(1, 1, -1), UP_SOUTH_WEST(-1, 1, 1), UP_SOUTH_EAST(1, 1, 1);

    public final int dx, dy, dz;
    /** How long one step is: 1 straight, √2 a flat diagonal, √3 a corner diagonal. */
    public final double length;

    Ray(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static final Ray[] BY_FACE = {DOWN, UP, NORTH, SOUTH, WEST, EAST};

    /** The straight way out of {@code face}. */
    public static Ray of(Direction face) {
        return BY_FACE[face.get3DDataValue()];
    }

    /** Whether it's one of the 6 straight ways. */
    public boolean straight() {
        return ordinal() < 6;
    }

    /** For a straight way, its face; for a diagonal, the face it leans on most (ties broken in a fixed order). */
    public Direction face() {
        return straight() ? Direction.values()[ordinal()] : Direction.getApproximateNearest(dx, dy, dz);
    }

    /**
     * The faces it travels across: one for a straight way, two for a flat diagonal, three for a corner one. Light
     * arriving this way enters a block through the opposites of these.
     */
    public List<Direction> faces() {
        List<Direction> faces = new ArrayList<>(3);
        if (dx != 0) faces.add(dx > 0 ? Direction.EAST : Direction.WEST);
        if (dy != 0) faces.add(dy > 0 ? Direction.UP : Direction.DOWN);
        if (dz != 0) faces.add(dz > 0 ? Direction.SOUTH : Direction.NORTH);
        return faces;
    }

    /** The block {@code steps} steps on from {@code from}. */
    public BlockPos step(BlockPos from, int steps) {
        return from.offset(dx * steps, dy * steps, dz * steps);
    }

    /** Which way it goes, as a unit vector. */
    public Vec3 unit() {
        return new Vec3(dx / length, dy / length, dz / length);
    }

    /** The step as a vector: block centre to the next block's centre. */
    public Vec3 vector() {
        return new Vec3(dx, dy, dz);
    }

    public Ray opposite() {
        return of(-dx, -dy, -dz);
    }

    /** The way with these steps; null for no step at all. */
    public static Ray of(int dx, int dy, int dz) {
        for (Ray ray : values()) if (ray.dx == dx && ray.dy == dy && ray.dz == dz) return ray;
        return null;
    }

    /** The one of the 26 nearest {@code delta}'s heading: what light aimed that way takes. */
    public static Ray nearest(Vec3 delta) {
        Ray best = UP;
        double bestDot = Double.NEGATIVE_INFINITY;
        double len = delta.length();
        if (len < 1.0e-9) return best;
        for (Ray ray : values()) {
            double dot = (delta.x * ray.dx + delta.y * ray.dy + delta.z * ray.dz) / (len * ray.length);
            if (dot > bestDot + 1.0e-9) {
                bestDot = dot;
                best = ray;
            }
        }
        return best;
    }
}
