package com.kadikular.quantimium.recipe;

import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The machines working out or making a craft right now, however they reach each other: a Tesseract link, the
 * logistics network, another mod's pipe or storage bus. A machine already on the chain offers nothing it would
 * have to make, so a loop ends there; past {@link #MAX_DEPTH} machines, none does. Only ever the server thread's.
 *
 * <p>What a machine can offer depends on which machines are on the chain when it's asked, so an answer is kept
 * per {@link #context()}: the same question from the same chain is worked out once a tick. And a tick's checks
 * have a {@link #BUDGET}: past it, machines asked from inside a chain offer only what they really hold until the
 * next tick, so no network, however tangled, can stall the server.
 */
public final class CraftChain {
    /** The most machines one craft reaches through: past this, only what's really there counts. */
    public static final int MAX_DEPTH = 4;
    /** Checks of what can be made, a tick, before chains stop reaching further. */
    public static final int BUDGET = 2048;

    private static final class State {
        int depth;
        long context;
        long epoch;
        long tick = Long.MIN_VALUE;
        int work;
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

    /** Which machines are on the chain, as one number: what a cached answer about offers is kept under. */
    public static long context() {
        State state = STATE.get();
        return state.context ^ ((long) state.depth << 56);
    }

    /** Counts transactions settled that touched what machines make on demand: answers last within one at most. */
    public static long epoch() {
        return STATE.get().epoch;
    }

    public static void settled() {
        STATE.get().epoch++;
    }

    /** It's now {@code gameTime}: a new tick's checks start from none. Call before asking {@link #full}. */
    public static void tick(long gameTime) {
        State state = STATE.get();
        if (state.tick != gameTime) {
            state.tick = gameTime;
            state.work = 0;
        }
    }

    /** One more check of what can be made. */
    public static void work() {
        STATE.get().work++;
    }

    /** Whether a machine asked now may offer only what it really holds: too deep, or this tick's checks spent. */
    public static boolean full() {
        State state = STATE.get();
        return state.depth >= MAX_DEPTH || state.depth > 0 && state.work > BUDGET;
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
