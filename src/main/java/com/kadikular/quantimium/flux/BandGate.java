package com.kadikular.quantimium.flux;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

/**
 * The flux band a machine works in, for rewards that switch on with the band (game plan B: running
 * hot pays). Read where the machine stands, blended between chunks, with hysteresis so a field
 * hovering at an edge switches it once; and where the field is heading, so a refusal can say
 * "Needs High flux: here Medium · heading for High".
 */
public final class BandGate {

    private FluxBand band = FluxBand.LOW;
    private FluxBand heading = FluxBand.LOW;

    /** Reads the field at {@code pos}; returns the band now worked in. */
    public FluxBand update(Level level, BlockPos pos) {
        band = FluxBand.of(QuantumFlux.sample(level, pos).flux(), band);
        double settling = QuantumFlux.chunkSettling(level, pos);
        heading = Double.isNaN(settling) ? band : FluxBand.of(settling);
        return band;
    }

    public FluxBand band() {
        return band;
    }

    public FluxBand heading() {
        return heading;
    }

    /** Sets the band directly, for a machine loading its saved state or a client mirroring it. */
    public void set(FluxBand band, FluxBand heading) {
        this.band = band;
        this.heading = heading;
    }

    /** "Needs High flux: here Medium · heading for High". */
    public static MutableComponent refusal(FluxBand needed, FluxBand here, FluxBand heading) {
        return Component.translatable("gui.quantimium.band_gate.needs", name(needed), where(here, heading));
    }

    /** "Medium", "Medium · heading for High" or "High · falling to Medium". */
    public static MutableComponent where(FluxBand here, FluxBand heading) {
        if (heading.ordinal() > here.ordinal()) return Component.translatable("flux.quantimium.trend.rising_to", name(here), name(heading));
        if (heading.ordinal() < here.ordinal()) return Component.translatable("flux.quantimium.trend.falling_to", name(here), name(heading));
        return name(here).copy();
    }

    public static Component name(FluxBand band) {
        return Component.translatable("flux.quantimium.band." + band.getSerializedName());
    }
}
