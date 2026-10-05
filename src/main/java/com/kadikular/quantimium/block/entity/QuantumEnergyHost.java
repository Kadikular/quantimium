package com.kadikular.quantimium.block.entity;

/**
 * A Quantimium machine that bills against an internal buffer.
 *
 * <p>The machines' published capabilities are insertion-only, so anything that needs to take power
 * back out — the Anomalite Crystal hazard, for one — has to reach the billing storage directly.
 * This exists so those callers can use {@code instanceof} instead of probing for a method by name
 * on the hot path.
 */
public interface QuantumEnergyHost {

    QuantumEnergyStorage getEnergyStorage();
}
