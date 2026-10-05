package com.kadikular.quantimium.phase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared tear geometry for the personal return rift and later anomaly-spawned entry rifts.
 * Width animation lives on the client; this is only where the tear sits and which way it faces.
 */
public record MirrorRift(BlockPos anchor, Direction facing, int shapeSeed) {

    public static final float OPEN_SECONDS = 0.5f;
    public static final int OPEN_TICKS = 10;

    public Vec3 centre() {
        Direction tangent = facing.getClockWise();
        return new Vec3(
                anchor.getX() + 0.5 + tangent.getStepX() * 0.5,
                anchor.getY() + 1.175,
                anchor.getZ() + 0.5 + tangent.getStepZ() * 0.5);
    }

    public AABB bounds(float width) {
        Vec3 centre = centre();
        double half = 0.7 * Math.max(0.05, width);
        return new AABB(centre.x - half, anchor.getY(), centre.z - half,
                centre.x + half, anchor.getY() + 2.2, centre.z + half);
    }
}
