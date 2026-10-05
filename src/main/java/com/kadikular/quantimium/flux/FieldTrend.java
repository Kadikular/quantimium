package com.kadikular.quantimium.flux;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Where a chunk's flux is heading, in words: rising to a band, falling to one, or steady. Read from
 * the level the field is settling at ({@link ChunkFlux#settling}), so a slow field still answers at
 * once when a machine starts or stops: "Medium, heading for High".
 */
public enum FieldTrend {
    RISING, STEADY, FALLING;

    /** How far the settling level must be from the value to count as moving. */
    private static final double MARGIN = 0.05;

    public static FieldTrend of(double value, double settling) {
        if (Double.isNaN(settling)) return STEADY;
        if (settling > value * (1.0 + MARGIN) + 1.0) return RISING;
        if (settling < value * (1.0 - MARGIN) - 1.0) return FALLING;
        return STEADY;
    }

    /** "Medium · heading for High", "Medium · steady", "High · falling to Medium". */
    public static MutableComponent describe(double value, double settling) {
        FluxBand band = FluxBand.of(value);
        Component bandName = Component.translatable("flux.quantimium.band." + band.getSerializedName());
        FieldTrend trend = of(value, settling);
        FluxBand heading = Double.isNaN(settling) ? band : FluxBand.of(settling);
        Component headingName = Component.translatable("flux.quantimium.band." + heading.getSerializedName());
        return switch (trend) {
            case STEADY -> Component.translatable("flux.quantimium.trend.steady", bandName);
            case RISING -> heading == band ? Component.translatable("flux.quantimium.trend.rising", bandName)
                    : Component.translatable("flux.quantimium.trend.rising_to", bandName, headingName);
            case FALLING -> heading == band ? Component.translatable("flux.quantimium.trend.falling", bandName)
                    : Component.translatable("flux.quantimium.trend.falling_to", bandName, headingName);
        };
    }
}
