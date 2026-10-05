// Path: src/main/java/com/kadikular/quantimium/block/entity/QuantumEnergyStorage.java
package com.kadikular.quantimium.block.entity;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class QuantumEnergyStorage extends SimpleEnergyHandler {

    public QuantumEnergyStorage(int capacity, int maxReceive, int maxExtract) {
        super(capacity, maxReceive, maxExtract);
    }

    public int getEnergyStored() {
        return energy;
    }

    public int getMaxEnergyStored() {
        return capacity;
    }

    // Direct setter for world load NBT restoration
    public void setEnergy(int energy) {
        this.energy = Math.clamp(energy, 0, capacity);
    }

    /** Internal machine billing, deliberately independent of the external transfer limit. */
    public int consume(long requested) {
        int taken = (int) Math.min(energy, Math.max(0, requested));
        energy -= taken;
        return taken;
    }

    /** Called whenever energy has been accepted from outside, so the owner can save and resync. */
    protected void onReceived() {}

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int inserted = super.insert(amount, transaction);
        if (inserted > 0) onReceived();
        return inserted;
    }
}
