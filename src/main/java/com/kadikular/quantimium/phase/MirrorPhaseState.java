package com.kadikular.quantimium.phase;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Session-only server state. Exiting leaves the player at their current same-world position. */
public record MirrorPhaseState(boolean active, long entryTick, ResourceKey<Level> entryDimension,
                               Vec3 entryPosition) {

    public static MirrorPhaseState inactive() {
        return new MirrorPhaseState(false, 0L, Level.OVERWORLD, Vec3.ZERO);
    }
}
