package com.kadikular.quantimium.util;

/**
 * A slot-based view whose pulls from some slots do work rather than just move items: a Quantum
 * Crafter's output shows what a pull would craft, and pulling it runs the craft (energy, flux,
 * ingredients). {@link LegacyItems} puts such pulls off until the transaction commits, since a
 * transaction that is only asking ("simulate") is aborted, and the work cannot be rolled back.
 */
public interface OnDemandSlots {
    /** Whether pulling {@code slot} now would do work rather than hand over items already there. */
    boolean producesOnDemand(int slot);
}
