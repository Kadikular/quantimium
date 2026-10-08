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

    /** The blocks a prism is linked to: light goes to each along a straight line, at any angle. */
    default List<BlockPos> targets() {
        return List.of();
    }

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

    /** For a cavity: it stores light, charged by emitters' spare power and given out to blocks. */
    default boolean buffers() {
        return false;
    }

    /** For a cavity: how much it can take this tick. */
    default long room() {
        return 0;
    }

    /** For a cavity: takes {@code fe} in. */
    default void fill(long fe) {}

    /** What this relay takes of what passes, as flux: a mirror's tax, or nothing for a photodetector. */
    default double tax() {
        return LaserTuning.HOP_TAX;
    }

    /** The network has traced this node's beams, or worked out what flows along them: for rendering. */
    void showBeams(List<LaserBeam> beams);
}
