package com.kadikular.quantimium.flux;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Log-spaced bands: a label on the field's continuous value, not its physics (see {@link FieldModel}).
 * The same five name flux (capability) and anomaly (danger).
 */
public enum FluxBand implements StringRepresentable {
    LOW(0),
    MEDIUM(100),
    HIGH(1_000),
    CRITICAL(10_000),
    SINGULARITY(100_000);

    /** Fraction of a band's lower bound a reading must fall below to drop out of it, once in. */
    public static final double HYSTERESIS = 0.85;

    private final double minFlux;

    FluxBand(double minFlux) {
        this.minFlux = minFlux;
    }

    public double minFlux() {
        return minFlux;
    }

    /**
     * The top of this band: where the next begins. Singularity is open-ended, so its "top" is ten
     * times its start, which is what containment rated for Singularity holds.
     */
    public double ceiling() {
        return this == SINGULARITY ? minFlux * 10.0 : values()[ordinal() + 1].minFlux;
    }

    /**
     * The band {@code value} reads as, given it read {@code previous} before: it climbs into a band
     * at the band's start, but only drops out below {@link #HYSTERESIS} of it. For anything that
     * switches on a band, so a reading hovering at an edge switches it once.
     */
    public static FluxBand of(double value, FluxBand previous) {
        FluxBand band = of(value);
        if (previous != null && band.ordinal() < previous.ordinal() && value >= previous.minFlux * HYSTERESIS) {
            return previous;
        }
        return band;
    }

    /** Extra FE percent charged on Quantimium work while this band is this chunk's anomaly.
     *  Runtime values come from {@link com.kadikular.quantimium.Config}; these are the defaults. */
    public int defaultSurchargePercent() {
        return switch (this) {
            case LOW -> 0;
            case MEDIUM -> 25;
            case HIGH -> 50;
            case CRITICAL -> 100;
            case SINGULARITY -> 200;
        };
    }

    public static FluxBand of(double flux) {
        FluxBand best = LOW;
        for (FluxBand band : values()) {
            if (flux >= band.minFlux) best = band;
        }
        return best;
    }

    /** True if {@code actual} is this band or below (a High shield covers Medium, not Critical). */
    public boolean covers(FluxBand actual) {
        return actual != null && actual.ordinal() <= ordinal();
    }

    /** Comparator / redstone strength for detectors: Low silent, then stepped through Singularity. */
    public int detectorSignal() {
        return switch (this) {
            case LOW -> 0;
            case MEDIUM -> 3;
            case HIGH -> 7;
            case CRITICAL -> 11;
            case SINGULARITY -> 15;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The band called {@code name} ("low" … "singularity"), or null. */
    @Nullable
    public static FluxBand byName(String name) {
        for (FluxBand band : values()) {
            if (band.getSerializedName().equals(name)) return band;
        }
        return null;
    }
}
