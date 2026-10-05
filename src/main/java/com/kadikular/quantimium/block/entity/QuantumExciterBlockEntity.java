package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.block.QuantumExciterBlock;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;

/**
 * Basic Quantum Exciter: burns FE at 10× flux efficiency to hold Medium flux. It works in proportion
 * to how far the chunk is below {@link #TARGET}, counting flux still easing in, so it settles in the
 * middle of Medium instead of switching on and off round it.
 */
public class QuantumExciterBlockEntity extends BlockEntity implements QuantumEnergyHost, FluxMeterReadout {

    /** Purpose-built emitters are far more efficient than accidental factory spill. */
    public static final double EFFICIENCY = 10.0;
    /** Where it aims: it works flat out at no flux and not at all from here up. */
    public static final double TARGET = 400.0;
    public static final int FE_PER_TICK = 100;
    public static final int ENERGY_CAPACITY = 100_000;
    public static final int MAX_RECEIVE = 2_000;

    private final QuantumEnergyStorage energyStorage =
            new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
                @Override
                protected void onReceived() {
                    setChanged();
                }
            };

    private int currentPowerUse;
    private int averagePowerUse;
    private int powerSampleIndex;
    private final int[] powerSamples = new int[20];
    private boolean exciting;

    public QuantumExciterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_EXCITER_BE.get(), pos, state);
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public int getAveragePowerUse() {
        return averagePowerUse;
    }

    /** How hard to work towards {@code target}: 1 at nothing, 0 at the target or above. */
    public static double throttle(double committed, double target) {
        if (target <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (target - committed) / target));
    }

    public int getCurrentPowerUse() {
        return currentPowerUse;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, QuantumExciterBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

        double throttle = throttle(QuantumFlux.chunkCommittedFlux(serverLevel, pos), TARGET);
        int want = (int) Math.ceil(FE_PER_TICK * throttle);
        boolean wasExciting = be.exciting;
        be.exciting = want > 0;
        if (be.exciting != wasExciting) be.setChanged();
        int spent = 0;

        if (want > 0 && be.energyStorage.getEnergyStored() >= want) {
            spent = be.energyStorage.consume(want);
            if (spent > 0) {
                QuantumFlux.emitFromEnergy(serverLevel, pos, spent, EFFICIENCY);
                be.setChanged();
            }
        }

        int leak = AnomalyEffects.passiveDrainFePerTick(serverLevel, pos);
        if (leak > 0) {
            leak = be.energyStorage.consume(leak);
            if (leak > 0) be.setChanged();
        }

        be.recordPowerUse(spent);

        boolean active = spent > 0;
        if (state.getValue(QuantumExciterBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(QuantumExciterBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private void recordPowerUse(int amount) {
        currentPowerUse = Math.max(0, amount);
        powerSamples[powerSampleIndex++ % powerSamples.length] = currentPowerUse;
        long total = 0;
        for (int sample : powerSamples) total += sample;
        averagePowerUse = (int) (total / powerSamples.length);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putBoolean("Exciting", exciting);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Energy")) energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        exciting = tag.getBooleanOr("Exciting", false);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return FluxMeterReadout.emitter(getAveragePowerUse(), EFFICIENCY);
    }
}
