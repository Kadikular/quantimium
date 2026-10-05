package com.kadikular.quantimium.flux;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity with something to tell the Flux Meter: the line shown after the chunk's reading when
 * the meter is used on it. Each block says it itself, so a machine from a partner mod's integration
 * (the ME Superposition Crafter) can take part without the meter knowing its class.
 */
public interface FluxMeterReadout {

    /** This block's line, or null for nothing beyond the chunk's reading. Server side. */
    @Nullable
    MutableComponent fluxMeterLine();

    /**
     * The line for a machine that emits flux for the FE it spends: its average draw, and the flux a
     * second that puts into the chunk it stands in (its share of what it spreads over the 3×3).
     */
    static MutableComponent emitter(int averageFe) {
        return emitter(averageFe, 1.0);
    }

    /** As {@link #emitter(int)}, for hardware that makes {@code efficiency} times the usual flux. */
    static MutableComponent emitter(int averageFe, double efficiency) {
        double fluxPerSecond = averageFe * efficiency * 20.0 / QuantumFlux.FE_PER_FLUX * FieldModel.EMISSION_GAIN
                * QuantumFlux.share(0, 0, QuantumFlux.SOURCE_RADIUS);
        return Component.translatable("item.quantimium.flux_meter.machine", fe(averageFe), flux(fluxPerSecond))
                .withStyle(ChatFormatting.DARK_AQUA);
    }

    static String fe(int fePerTick) {
        return String.format("%,d", fePerTick);
    }

    static String flux(double flux) {
        if (flux < 10.0) return String.format("%.2f", flux);
        if (flux < 1000.0) return String.format("%.1f", flux);
        if (flux < 100_000.0) return String.format("%.0f", flux);
        return String.format("%.2e", flux);
    }
}
