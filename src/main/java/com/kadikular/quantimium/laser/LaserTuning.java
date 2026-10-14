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

    /** Emitter tiers: the most they send a tick, how far a run reaches, what they hold, and what they burn idle. */
    public enum Tier {
        FLASH(200, 8, 20_000, 0),
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

    /** What a Solar Emitter makes in full sun, FE/t; less as the sky dims, none at night. */
    public static final int SOLAR_FE = 20;
    /** The most a chain of Solar Emitters gives out of its last one, FE/t: eight in full sun. */
    public static final int SOLAR_CHAIN_MAX = 160;
    /** How far a Solar Emitter's light reaches a run. */
    public static final int SOLAR_RANGE = 8;

    /** What the flash emitter makes from burning: a furnace's fuel ticks, this much FE each. */
    public static final int FLASH_FE_PER_BURN_TICK = 100;

    // ---- lenses ----

    /** Amplify lenses that count: the first lets a Harvest beam cut as a diamond pickaxe; each doubles a hit. */
    public static final int AMPLIFY_LENSES = 3;
    /** FE to place a block from a sending Laser Port. */
    public static final long PLACE_FE = 200;
    /** Ticks between blocks a Placement beam places. */
    public static final int PLACE_COOLDOWN = 10;
    /** Ticks over which Insert and Extract beams made together are spread at the start, so they don't all look in one tick. */
    public static final int IDLE_RETRY = 10;
    /** Ticks a network whose blocks took no power waits before charging them again. */
    public static final int FULL_REST = 10;
    /** Ticks an area action takes a clear line to a block as still clear, before tracing it again. */
    public static final int SIGHT_KEPT = 10;
    /** Kinds of thing an Interact beam tries from its sending Laser Ports in one go, before it gives up till next time. */
    public static final int INTERACT_TRIES = 9;
    /** How long light carrying power blinds what looks into it: fifteen seconds. */
    public static final int BLIND_TICKS = 300;
    /** How near the beam's line an eye must be. */
    public static final double BLIND_REACH = 0.35;
    /** How much more a cut or a placement costs, done by an area prism. */
    public static final double AREA_COST = 1.5;
    /** Blocks an area prism's action looks through a tick for the next to work on. */
    public static final int AREA_SCAN = 64;
    /** Lines of sight an area prism's action traces a tick while it looks (each Speed lens more): the dear part of looking. */
    public static final int AREA_TRACES = 4;
    /** Ticks an area prism's action rests after a whole sweep of its area found nothing to do. */
    public static final int AREA_REST = 20;
    /** The largest an area may be on each side. Only what the prism's light reaches, and sees, is worked. */
    public static int areaMax() {
        return 2 * com.kadikular.quantimium.Config.maxLaserRange() + 1;
    }
    /** Range lenses that count: each doubles a run's reach, up to the configured most (64 by default). Light with an action goes 8, 16, 32, 64. */
    public static final int RANGE_LENSES = 4;
    /** Harmonic lenses that count: a level of Fortune each. */
    public static final int HARMONIC_LENSES = 3;
    /** Speed lenses that count: each doubles how fast an action works, to eight times. */
    public static final int SPEED_LENSES = 3;
    /** How far a run of light with an action reaches (Pickup counts), before Range lenses. */
    public static final int ACTION_REACH = 8;
    /** FE to break a block, for each point of its hardness (stone is 1.5, obsidian 50); at least one point's worth. */
    public static final long HARVEST_FE_PER_HARDNESS = 400;
    /** Ticks between blocks a Harvest lens breaks, however much power it has. */
    public static final int HARVEST_COOLDOWN = 10;
    /** What a Damage lens's beam deals each hit, in half-hearts. */
    public static final float DAMAGE_PER_HIT = 4.0f;
    /** FE for each hit on each thing in the beam, paid when it lands: within what an Emitter I sends in a tick. */
    public static final long DAMAGE_FE_PER_HIT = 1_000;
    /** Ticks between hits. */
    public static final int DAMAGE_INTERVAL = 10;
    /** Smelting and Crushing lenses that count as steps on one light. */
    public static final int PROCESS_STEPS = 8;
    /** FE a Crushing lens takes for each item it crushes. */
    public static final long CRUSH_FE_PER_ITEM = 25;
    /** FE a Smelting lens takes for each item it smelts on the way up. */
    public static final long SMELT_FE_PER_ITEM = 25;
    /** Items Insert or Extract moves at once; each Speed lens doubles it (16, 32, 64). */
    public static final int MOVE_ITEMS = 8;
    /** FE to carry one stack back up the beam. */
    public static final long PICKUP_FE_PER_STACK = 200;
    /** FE a Logistics Controller pays for each transfer (up to a stack). */
    public static final long LOGISTICS_FE_PER_MOVE = 100;
    /** What a Logistics Controller holds, and takes in a tick. */
    public static final int LOGISTICS_CAPACITY = 100_000;
    public static final int LOGISTICS_RECEIVE = 1_000;
    /** Ticks between the beam's sweeps for items lying in it. */
    public static final int PICKUP_INTERVAL = 10;
}
