package com.kadikular.quantimium.block.entity.simulation;

/**
 * What a machine can learn about being run inside a Quantum Simulator, during its virtual tick.
 *
 * <p>Nearly every machine needs nothing from this: the simulator measures what one copy did and
 * multiplies it. A machine whose work is an effect on the world rather than items or fluids (the
 * Zeno Field Controller's extra random ticks) cannot be multiplied from outside, so it asks how many
 * copies it stands for and does that much itself, drawing one copy's energy for the simulator to
 * bill per copy. Server thread only; outside a virtual tick it is 1.
 */
public final class SimulationContext {

    private static int copies = 1;
    private static boolean active;

    private SimulationContext() {}

    /** How many copies of the machine the tick in progress stands for. */
    public static int copies() {
        return copies;
    }

    /**
     * Whether the tick in progress is a simulator's virtual tick. The simulator bills and emits flux
     * for what the machine draws, so a machine that emits its own flux should not do it here as well.
     */
    public static boolean active() {
        return active;
    }

    /** A simulator inside a simulator multiplies: 8x within 8x stands for 64 copies. */
    static void run(int batch, Runnable tick) {
        int previousCopies = copies;
        boolean previousActive = active;
        copies = Math.max(1, previousCopies * Math.max(1, batch));
        active = true;
        try {
            tick.run();
        } finally {
            copies = previousCopies;
            active = previousActive;
        }
    }
}
