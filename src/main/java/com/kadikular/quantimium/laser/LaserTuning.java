package com.kadikular.quantimium.laser;

/**
 * Lasers' numbers in one place: starting values for the first playtest (Quantimium Lasers design doc,
 * "Power"), to move to the config once they've settled.
 */
public final class LaserTuning {

    private LaserTuning() {}

    /** What each relay a beam passes takes of what passes, as flux. */
    public static final double HOP_TAX = 0.01;
    /** How far a mirror's light reaches before it needs refocusing again. */
    public static final int RELAY_RANGE = 32;
    /** How far a prism reaches, in a straight line to each block it's linked to, and how many it links. */
    public static final int PRISM_RANGE = 24;
    public static final int PRISM_LINKS = 8;

    /** An optical cavity: the longest gap between its two mirrors, what each holds per block of gap, its rate in and out, and its leak. */
    public static final int CAVITY_MAX_GAP = 16;
    public static final long CAVITY_FE_PER_BLOCK = 10_000_000;
    public static final long CAVITY_RATE = 512_000;
    /** The share of what a cavity mirror holds that leaks away each tick, as flux. */
    public static final double CAVITY_LEAK = 0.00002;

    /** Emitter tiers: the most they send a tick, how far a run reaches, what they hold, and what they burn idle. */
    public enum Tier {
        FLASH(100, 8, 20_000, 0),
        ONE(2_000, 16, 200_000, 2),
        TWO(32_000, 32, 3_200_000, 16),
        THREE(512_000, 64, 51_200_000, 128);

        public final int rate;
        public final int range;
        public final int capacity;
        /** FE a tick an emitter that's on and has somewhere to send light spends just being ready. */
        public final int idle;

        Tier(int rate, int range, int capacity, int idle) {
            this.rate = rate;
            this.range = range;
            this.capacity = capacity;
            this.idle = idle;
        }
    }

    /** What the flash emitter makes from burning: a furnace's fuel ticks, this much FE each. */
    public static final int FLASH_FE_PER_BURN_TICK = 40;
}
