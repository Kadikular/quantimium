package com.kadikular.quantimium.laser;

import net.minecraft.core.Direction;

import java.util.List;
import java.util.Set;

/**
 * A laser node light ends at: the logistics blocks a network reaches (a Logistics Terminal, a Logistics
 * Materialiser, a Reactor Logistics Port). It passes nothing on and takes nothing of what arrives; it counts as a
 * relay only so the light reaching it is known. It draws no beams of its own.
 */
public interface LaserEndpoint extends LaserNode {

    @Override
    default Set<Direction> outputs() {
        return Set.of();
    }

    @Override
    default int range() {
        return LaserTuning.RELAY_RANGE;
    }

    @Override
    default boolean relays() {
        return true;
    }

    @Override
    default double tax() {
        return 0;
    }

    @Override
    default void showBeams(List<LaserBeam> beams) {
    }
}
