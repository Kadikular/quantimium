package com.kadikular.quantimium.recipe;

/**
 * The machines working out or making a craft right now, however they reach each other: a Tesseract link, the
 * logistics network, another mod's pipe or storage bus. A machine already on the chain offers nothing it would
 * have to make, so a loop ends there; past {@link #MAX_DEPTH} machines, none does. Only ever the server thread's.
 */
public final class CraftChain {
    /** The most machines one craft reaches through: past this, only what's really there counts. */
    public static final int MAX_DEPTH = 4;

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private CraftChain() {}

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        int[] depth = DEPTH.get();
        depth[0] = Math.max(0, depth[0] - 1);
    }

    /** How many machines are working out or making a craft now. */
    public static int depth() {
        return DEPTH.get()[0];
    }

    private static final ThreadLocal<long[]> EPOCH = ThreadLocal.withInitial(() -> new long[1]);

    /**
     * Counts transactions settled (committed or undone) that touched what machines make on demand: a cached
     * answer about what a machine could make is good only within one epoch, since a machine it draws on may have
     * just been put back.
     */
    public static long epoch() {
        return EPOCH.get()[0];
    }

    public static void settled() {
        EPOCH.get()[0]++;
    }

    /** Whether a machine asked now may offer only what it really holds. */
    public static boolean full() {
        return depth() >= MAX_DEPTH;
    }
}
