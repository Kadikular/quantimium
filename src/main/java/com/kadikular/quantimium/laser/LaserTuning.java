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
