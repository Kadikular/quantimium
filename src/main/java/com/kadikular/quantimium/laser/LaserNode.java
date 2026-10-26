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

    /**
     * For an Entangled Mirror: the laser block its Tesseract is bound to. Light reaching that block reaches this one
     * too, at once, however far and whatever's between; null for none, and for any other node.
     */
    @org.jetbrains.annotations.Nullable
    default BlockPos entangledSource() {
        return null;
    }

    /**
     * Whether light reaching it still reaches the Entangled Mirrors drawing on it while it's switched off: a Relay's
     * switch is only its emitters', so yes; a Laser Mirror's stops all its light.
     */
    default boolean passesEntangled() {
        return false;
    }

    /** For an Entangled Mirror: the way light it draws leaves it, out of its muzzle; null for any other node. */
    @org.jetbrains.annotations.Nullable
    default Direction entangledFace() {
        return null;
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
        return com.kadikular.quantimium.Config.relayLoss();
    }

    /** What light leaving by {@code face} loses on the way out, as flux: a Laser Focus's emitter's loss. */
    default double outTax(Direction face) {
        return 0;
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

    /** The faces light leaves by, given the ways it's travelling as it arrives: its outputs, unless it passes light straight on (a rift). */
    default Set<Direction> outputsFor(Set<Direction> arriving) {
        return outputs();
    }

    /** Whether light through it goes into the mirror, or back out of it: a Rift Lens. */
    /**
     * Lenses on one output face only (a Relay's emitter with a lens in it): what light leaving by {@code face}
     * goes through after the block's own. None for most.
     */
    default List<LensUse> lensesFor(Direction face) {
        return List.of();
    }

    /**
     * Whether light travelling {@code travel} as it arrives gets in: a Mirror takes light only through its two
     * sides, a Rift Lens only along its axis. Light that doesn't get in stops here: it's neither joined to what
     * leaves, nor paid through, nor followed back up.
     */
    default boolean accepts(Direction travel) {
        return true;
    }

    /**
     * Whether light travelling {@code ray} gets in. Light reaching a block always passes its middle (its focus): it gets
     * in if any face it crosses into the block by is one it takes light by. A mirror, still straight, takes only straight
     * light.
     */
    default boolean accepts(Ray ray) {
        if (ray.straight()) return accepts(ray.face());
        for (Direction travel : ray.faces()) if (accepts(travel)) return true;
        return false;
    }

    /** For a prism: the face of the linked block {@code target} its light goes in by; null for its middle. */
    @org.jetbrains.annotations.Nullable
    default Direction linkFace(BlockPos target) {
        return null;
    }

    /**
     * Whether it gives out light, and power, of its own: every source. A Solar Emitter both does and passes on
     * the light that reaches it, as a relay.
     */
    default boolean generates() {
        return !relays();
    }

    /** For a relay: the most FE a tick it lets through from other sources (a Solar Emitter's chain limit). */
    default long passLimit() {
        return Long.MAX_VALUE;
    }

    default boolean rifts() {
        return false;
    }

    /** Whether redstone can switch it on and off: the network then watches it, and lets no light through it while it's off. */
    default boolean switches() {
        return false;
    }

    /** One action that light through this relay was let through for is done: a pulse mirror closes. */
    default void pulseDone() {}

    /** One action down the beam out of {@code face} is done: a pulse on that face closes. */
    default void pulseDone(Direction face) {
        pulseDone();
    }

    /** What redstone has switched, as a number: a change in it is a change in the light (a Relay: which emitters are on). */
    default int switchState() {
        return enabled() ? 1 : 0;
    }

    /** For an area prism: the blocks the actions of light reaching it work on; null for any other node. */
    @org.jetbrains.annotations.Nullable
    default net.minecraft.world.level.levelgen.structure.BoundingBox area() {
        return null;
    }

    /** An area prism: the beams it shows to what it's working on, and exactly where each ends. */
    default void showWork(java.util.List<LaserBeam> beams, java.util.List<net.minecraft.world.phys.Vec3> points) {
        showBeams(beams);
    }

    /** For an area prism: whether {@code pos}, in its area's box, is in its area (a sphere's box has corners that aren't). */
    default boolean inArea(BlockPos pos) {
        return true;
    }

    /** Puts back what a sending Laser Port sent but couldn't be used; what didn't fit. */
    default net.minecraft.world.item.ItemStack takeBack(net.minecraft.world.item.ItemStack stack) {
        return stack;
    }

    /** Whether light through it loses everything it did, and items can't pass it: an Isolator. */
    default boolean isolates() {
        return false;
    }

    /** Whether this sends items down the beam, from its slots: a sending Laser Port. */
    default boolean sends() {
        return false;
    }

    /** Takes up to one stack the {@code wants} predicate accepts from a sending Laser Port's slots (or with {@code simulate}, says what it would). */
    default net.minecraft.world.item.ItemStack send(java.util.function.Predicate<net.minecraft.world.item.ItemStack> wants, int max, boolean simulate) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /**
     * Whether items a beam brings back stop here: a collecting Laser Port. Pickup sends them to the nearest one up
     * the beam.
     */
    default boolean collects() {
        return false;
    }

    /**
     * For a drop of several things at once: what of {@code stack} it would take, after what it already took of the
     * same drop. Inventories it fills are filled inside {@code transaction}, which the caller rolls back; what it holds
     * itself is pretended in {@code scratch}. Alone, as a simulated {@link #collect}.
     */
    default net.minecraft.world.item.ItemStack collectAlong(net.minecraft.world.item.ItemStack stack,
            net.neoforged.neoforge.transfer.transaction.TransactionContext transaction, java.util.Map<Object, Object> scratch) {
        return collect(stack, true);
    }

    /** Takes what it can of {@code stack}, a collecting Laser Port (or with {@code simulate}, says what it would); what's left. */
    default net.minecraft.world.item.ItemStack collect(net.minecraft.world.item.ItemStack stack, boolean simulate) {
        return stack;
    }

    /** What it does on a logistics network (a Logistics Receptacle, a Laser Port), or null when it takes no part. */
    @org.jetbrains.annotations.Nullable
    default LogisticsMember logistics() {
        return null;
    }

    /**
     * Whether light ending on it charges it, as it would any block that takes power, besides passing on: a Logistics
     * Controller fills its own store this way. Its energy is the block's own (Capabilities.Energy).
     */
    default boolean chargedByLight() {
        return false;
    }

    /**
     * The face of a block beside it that light reaching it powers, as light ending on that block would (a Logistics
     * Receptacle set to power the machine it's on); null for none.
     */
    @org.jetbrains.annotations.Nullable
    default net.minecraft.core.Direction feeds() {
        return null;
    }
}
