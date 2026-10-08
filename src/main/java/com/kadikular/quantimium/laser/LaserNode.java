package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Set;

/**
 * A block entity that's part of a laser network: something that starts beams or passes them on.
 * {@link LaserNetworks} finds them, traces their beams and moves power along them; the node only says
 * where its light goes and, for a source, how much it has to give.
 */
public interface LaserNode {

    BlockPos nodePos();

    /** The faces light leaves from. */
    Set<Direction> outputs();

    /** How far one straight run of this node's light reaches before it fades. */
    int range();

    /** Whether light that reaches this node goes on out of its outputs: a mirror does, an emitter doesn't. */
    boolean relays();

    /** Whether it's switched on (redstone allowing): an off source sends nothing, an off relay passes nothing. */
    default boolean enabled() {
        return true;
    }

    /** For a source: how much power it could send this tick. Zero for a relay. */
    default long available() {
        return 0;
    }

    /** For a source: takes {@code fe} it sent this tick. */
    default void draw(long fe) {}

    /** The network has traced this node's beams, or worked out what flows along them: for rendering. */
    void showBeams(List<LaserBeam> beams);
}
