package com.kadikular.quantimium.recipe;

import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The machines working out or making a craft right now, however they reach each other: a Tesseract link, the
 * logistics network, another mod's pipe or storage bus. A machine already on the chain offers nothing it would
 * have to make, so a loop ends there; past {@link #MAX_DEPTH} machines, none does. Only ever the server thread's.
 *
 * <p>What a machine can offer depends on which machines are on the chain when it's asked, so an answer is kept
 * per {@link #context()}: the same question from the same chain is worked out once a tick. What a network shows
 * doesn't come from here at all: it's the network's {@link com.kadikular.quantimium.laser.OfferTable}.
 */
public final class CraftChain {
    /** The most machines one craft reaches through: past this, only what's really there counts. */
    public static final int MAX_DEPTH = 4;

    private static final class State {
        int depth;
        long context;
        long epoch;
        /** From how deep on the chain machines may only use what's stored, making nothing (none: never). */
        int storedFrom = Integer.MAX_VALUE;
        /** What crafts up the chain are making now, and how many of them make each. */
        final java.util.Map<net.minecraft.world.item.Item, Integer> making = new java.util.HashMap<>();
        long makingHash;
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private CraftChain() {}

    /** The machine {@code id} (a block position, packed) joins the chain. */
    public static void enter(long id) {
        State state = STATE.get();
        state.depth++;
        state.context ^= mix(id);
    }

    public static void exit(long id) {
        State state = STATE.get();
        state.depth = Math.max(0, state.depth - 1);
        state.context ^= mix(id);
    }

    private static long mix(long id) {
        long h = id * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    /** How many machines are working out or making a craft now. */
    public static int depth() {
        return STATE.get().depth;
    }

    /** Which machines are on the chain, and what they're making, as one number: what a cached answer about offers is kept under. */
    public static long context() {
        State state = STATE.get();
        return state.context ^ state.makingHash ^ ((long) state.depth << 56);
    }

    /**
     * A craft making {@code outputs} starts: till it's done, a craft further down the chain that would use any of
     * them is a loop (ingots for nuggets for ingots), and isn't made.
     */
    public static void startMaking(java.util.Collection<net.minecraft.world.item.Item> outputs) {
        State state = STATE.get();
        for (net.minecraft.world.item.Item item : outputs) {
            state.making.merge(item, 1, Integer::sum);
            state.makingHash += mix(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(item) + 1L);
        }
    }

    public static void stopMaking(java.util.Collection<net.minecraft.world.item.Item> outputs) {
        State state = STATE.get();
        for (net.minecraft.world.item.Item item : outputs) {
            state.making.computeIfPresent(item, (k, n) -> n > 1 ? n - 1 : null);
            state.makingHash -= mix(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(item) + 1L);
        }
    }

    /** Whether a craft up the chain is making {@code item} now: a craft using it would loop back. */
    public static boolean makingUpChain(net.minecraft.world.item.Item item) {
        return STATE.get().making.containsKey(item);
    }

    /** Counts transactions settled that touched what machines make on demand: answers last within one at most. */
    public static long epoch() {
        return STATE.get().epoch;
    }

    public static void settled() {
        STATE.get().epoch++;
    }

    /** Whether a machine asked now may offer only what it really holds: the chain is as long as it goes. */
    public static boolean full() {
        return STATE.get().depth >= MAX_DEPTH;
    }

    /**
     * Runs {@code pull}, a pull from a machine that may craft only from what's stored: whatever it draws on while it
     * crafts makes nothing for it.
     */
    public static <T> T storedOnly(java.util.function.Supplier<T> pull) {
        State state = STATE.get();
        int prior = state.storedFrom;
        state.storedFrom = Math.min(prior, state.depth + 1);
        try {
            return pull.get();
        } finally {
            state.storedFrom = prior;
        }
    }

    /** Whether a machine asked now may hand over only what's stored: one up the chain crafts from stored items only. */
    public static boolean storedOnly() {
        State state = STATE.get();
        return state.depth >= state.storedFrom;
    }

    /**
     * Whether an answer worked out now may be kept: not while a transaction is closing, when nothing can be read
     * from another inventory and every answer about what could be made comes out empty.
     */
    public static boolean cacheable() {
        Transaction.Lifecycle lifecycle = Transaction.getLifecycle();
        return lifecycle == Transaction.Lifecycle.NONE || lifecycle == Transaction.Lifecycle.OPEN;
    }
}
