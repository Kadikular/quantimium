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

    /** Lets go of a linked block that's gone. */
    default void forget(BlockPos target) {}

    /**
     * Whether light leaving this node starts a fresh run at its own range: a mirror refocuses it; a
     * photodetector only lets it through, so the light goes on with what range it had left.
     */
    default boolean refocuses() {
        return true;
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

    /** Whether a link to a block that's gone is kept (a prism set to), rather than let go. */
    default boolean keepsLinks() {
        return false;
    }

    /** The light a source gives out: plain light at its range, or for a cavity, what it was filled with. */
    default Light emits() {
        return Light.from(nodePos(), range());
    }

    /** A cavity filled with {@code light}: whether it now remembers more of what light does. */
    default boolean absorb(Light light) {
        return false;
    }

    /** The lenses this node holds, in the order light meets them: a Lens Mount's. */
    default List<LensUse> lenses() {
        return List.of();
    }

    /** Whether redstone can switch it on and off: the network then watches it, and lets no light through it while it's off. */
    default boolean switches() {
        return false;
    }

    /** One action that light through this relay was let through for is done: a pulse mirror closes. */
    default void pulseDone() {}

    /** For an area prism: the blocks the actions of light reaching it work on; null for any other node. */
    @org.jetbrains.annotations.Nullable
    default net.minecraft.world.level.levelgen.structure.BoundingBox area() {
        return null;
    }

    /** An area prism: the beams it shows to what it's working on, and exactly where each ends. */
    default void showWork(java.util.List<LaserBeam> beams, java.util.List<net.minecraft.world.phys.Vec3> points) {
        showBeams(beams);
    }

    /** Puts back what a Sender sent but couldn't be used; what didn't fit. */
    default net.minecraft.world.item.ItemStack takeBack(net.minecraft.world.item.ItemStack stack) {
        return stack;
    }

    /** Whether light through it loses everything it did, and items can't pass it: an Isolator. */
    default boolean isolates() {
        return false;
    }

    /** Whether this sends items down the beam, from its slots: a Sender. */
    default boolean sends() {
        return false;
    }

    /** Takes up to one stack the {@code wants} predicate accepts from a Sender's slots (or with {@code simulate}, says what it would). */
    default net.minecraft.world.item.ItemStack send(java.util.function.Predicate<net.minecraft.world.item.ItemStack> wants, int max, boolean simulate) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /**
     * Whether items a beam brings back stop here: a Collector. Pickup sends them to the nearest one up
     * the beam.
     */
    default boolean collects() {
        return false;
    }

    /** Takes what it can of {@code stack}, a Collector (or with {@code simulate}, says what it would); what's left. */
    default net.minecraft.world.item.ItemStack collect(net.minecraft.world.item.ItemStack stack, boolean simulate) {
        return stack;
    }
}
