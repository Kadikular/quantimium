package com.kadikular.quantimium.block.entity.simulation;

/**
 * A machine whose work is an effect on the world rather than items or fluids, which the Quantum
 * Simulator should still run although it has no inventory to project into.
 *
 * <p>The simulator cannot see or multiply such work, so the machine does it for every copy itself,
 * reading {@link SimulationContext#copies()}, while drawing only one copy's energy. Having no input
 * slots, it is billed like a generator: its draw, for every copy.
 */
public interface SimulatedEffect {

    /**
     * Whether this machine's work is an effect right now. A Quantum Simulator answers for whatever it
     * holds, so a simulator running a Zeno Field Controller, nested in another, is run and billed
     * like the controller itself.
     */
    default boolean isSimulatedEffect() {
        return true;
    }
}
