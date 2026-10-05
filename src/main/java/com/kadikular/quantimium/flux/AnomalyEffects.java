package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Ill effects driven by <em>this chunk's</em> anomaly, not flux and not a neighbour's peak.
 * Craft surcharge and passive buffer leak are both config-gated; see {@link com.kadikular.quantimium.Config}.
 * Powered Anomaly Containment zeroes tax, leak, and coupling in this chunk when the anomaly
 * band is within the shield's rating (High for the basic block). Over-cap coverage does nothing.
 */
public final class AnomalyEffects {

    private AnomalyEffects() {}

    public static FluxBand anomalyBand(Level level, BlockPos pos) {
        if (level == null || pos == null) return FluxBand.LOW;
        return QuantumFlux.chunk(level, pos).anomalyBand();
    }

    public static double feMultiplier(Level level, BlockPos pos) {
        if (level == null || pos == null) return 1.0;
        if (QuantumFlux.chunkContained(level, pos)) return 1.0;
        return feMultiplier(anomalyBand(level, pos));
    }

    public static double feMultiplier(FluxBand band) {
        return 1.0 + surchargePercent(band) / 100.0;
    }

    public static int surchargePercent(Level level, BlockPos pos) {
        if (QuantumFlux.chunkContained(level, pos)) return 0;
        return surchargePercent(anomalyBand(level, pos));
    }

    public static int surchargePercent(FluxBand band) {
        return Config.surchargePercent(band);
    }

    public static int scaleFe(int base, double multiplier) {
        if (base <= 0 || multiplier <= 1.0) return Math.max(0, base);
        long scaled = Math.round(base * multiplier);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(base, scaled));
    }

    public static long scaleFe(long base, double multiplier) {
        if (base <= 0L || multiplier <= 1.0) return Math.max(0L, base);
        if (multiplier >= (double) Long.MAX_VALUE / base) return Long.MAX_VALUE;
        long scaled = Math.round(base * multiplier);
        return Math.max(base, scaled);
    }

    public static int scaleFe(int base, Level level, BlockPos pos) {
        return scaleFe(base, feMultiplier(level, pos));
    }

    public static long scaleFe(long base, Level level, BlockPos pos) {
        return scaleFe(base, feMultiplier(level, pos));
    }

    /**
     * Bakes a price multiplier into a one-run craft before batch sizing: the anomaly surcharge, times
     * the Crafter's band tax, which can bring it below the base price (running hot pays).
     */
    public static ResolvedCraft tax(ResolvedCraft craft, double multiplier) {
        if (craft == null || multiplier == 1.0 || craft.feCost() <= 0) return craft;
        int scaled = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(craft.feCost() * multiplier)));
        if (scaled == craft.feCost()) return craft;
        return new ResolvedCraft(craft.recipeId(), craft.withdrawals(), craft.outputs(), scaled,
                craft.batchSize(), craft.energyCapped(), craft.availableRuns());
    }

    /**
     * FE to pull from a machine buffer this tick. Does not emit flux — idle leak is a tax, not more
     * field. Zero at Low anomaly or when the config toggle is off.
     */
    public static int passiveDrainFePerTick(Level level, BlockPos pos) {
        if (level == null || pos == null) return 0;
        if (QuantumFlux.chunkContained(level, pos)) return 0;
        return Config.passiveDrainFePerTick(anomalyBand(level, pos));
    }
}
