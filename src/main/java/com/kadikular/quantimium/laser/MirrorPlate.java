package com.kadikular.quantimium.laser;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which way a Laser Mirror's plate faces: flat to one of the six faces, or turned from it towards a neighbouring face
 * by 22.5° or 45°. 42 in all: 6 flat, 24 at 22.5° and 12 at 45° (45° from one face towards another is 45° from that one
 * back, so each is counted once). Light reflects off it as off a real mirror; what leaves is one of the 26 ways light
 * travels, the nearest if the reflection itself isn't one.
 *
 * @param face   the face it's turned from (flat: the face it faces)
 * @param toward the neighbouring face it's turned towards; null when flat
 * @param steps  0 flat, 1 for 22.5°, 2 for 45°
 */
public record MirrorPlate(Direction face, @Nullable Direction toward, int steps) {

    public static final List<MirrorPlate> ALL;

    static {
        List<MirrorPlate> all = new ArrayList<>();
        for (Direction face : Direction.values()) all.add(new MirrorPlate(face, null, 0));
        for (Direction face : Direction.values()) {
            for (Direction toward : Direction.values()) {
                if (toward.getAxis() == face.getAxis()) continue;
                all.add(new MirrorPlate(face, toward, 1));
            }
        }
        for (Direction face : Direction.values()) {
            for (Direction toward : Direction.values()) {
                if (toward.getAxis() == face.getAxis() || toward.ordinal() < face.ordinal()) continue;
                all.add(new MirrorPlate(face, toward, 2));
            }
        }
        ALL = Collections.unmodifiableList(all);
    }

    /** Flat, facing {@code face}. */
    public static MirrorPlate flat(Direction face) {
        return ALL.get(face.ordinal());
    }

    /**
     * Turned from {@code face} towards {@code toward} by {@code steps} of 22.5° (0 flat). 45° from either face of a pair
     * is the same plate.
     */
    public static MirrorPlate of(Direction face, Direction toward, int steps) {
        if (steps <= 0) return flat(face);
        if (steps >= 2 && toward.ordinal() < face.ordinal()) return new MirrorPlate(toward, face, 2);
        return new MirrorPlate(face, toward, Math.min(2, steps));
    }

    /** Its place in {@link #ALL}: what it's saved as. */
    public int index() {
        return ALL.indexOf(this);
    }

    public static MirrorPlate byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : ALL.get(Direction.NORTH.ordinal());
    }

    /** Which way its mirrored side faces: a unit vector. */
    public Vec3 normal() {
        Vec3 f = Vec3.atLowerCornerOf(face.getUnitVec3i());
        if (toward == null) return f;
        double angle = Math.toRadians(22.5 * steps);
        return f.scale(Math.cos(angle)).add(Vec3.atLowerCornerOf(toward.getUnitVec3i()).scale(Math.sin(angle)));
    }

    /**
     * What a mirror set the old way (light in by one side, out by another; flat when they're the same) is now: its plate
     * faces half way between the two.
     */
    public static MirrorPlate fromSides(Direction input, Direction output) {
        return input == output ? flat(input) : output.getAxis() == input.getAxis() ? flat(input) : of(input, output, 2);
    }

    /** The plate nearest {@code normal}. */
    public static MirrorPlate nearest(Vec3 normal) {
        MirrorPlate best = ALL.getFirst();
        double bestDot = Double.NEGATIVE_INFINITY;
        for (MirrorPlate plate : ALL) {
            double dot = plate.normal().dot(normal);
            if (dot > bestDot) {
                bestDot = dot;
                best = plate;
            }
        }
        return best;
    }

    /**
     * Light travelling {@code travel} (a unit vector) off its mirrored side: the way it leaves, one of the 26 (the nearest,
     * if the true reflection isn't one). Null if it hits the back or edge, where it stops; with {@code twoSided} (a
     * splitter's half-silvered plate) both sides reflect.
     */
    @Nullable
    public Ray reflect(Vec3 travel, boolean twoSided) {
        Vec3 n = normal();
        double dot = travel.dot(n);
        if (Math.abs(dot) < 1.0e-6 || dot > 0 && !twoSided) return null;
        return Ray.nearest(travel.subtract(n.scale(2 * dot)));
    }

    /** The plate turned as a structure is. */
    public MirrorPlate rotate(Rotation rotation) {
        return toward == null ? flat(rotation.rotate(face)) : of(rotation.rotate(face), rotation.rotate(toward), steps);
    }

    /** The plate mirrored as a structure is. */
    public MirrorPlate mirror(Mirror mirror) {
        return toward == null ? flat(mirror.mirror(face)) : of(mirror.mirror(face), mirror.mirror(toward), steps);
    }

    /** Whether it faces at all towards {@code mount}, where the mirror stands: it can't. */
    public boolean facesInto(Direction mount) {
        return normal().dot(Vec3.atLowerCornerOf(mount.getUnitVec3i())) > 1.0e-6;
    }
}
