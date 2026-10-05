package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.flux.FluxBand;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Shared energy-bar text for Quantimium machines. Negative power figures are omitted. */
public final class EnergyBarTooltip {

    private EnergyBarTooltip() {}

    public static List<Component> lines(int stored, int capacity, int currentPerTick,
                                        int averagePerTick, boolean includesOverhead) {
        return lines(stored, capacity, currentPerTick, averagePerTick, includesOverhead, 0, null, 0);
    }

    public static List<Component> lines(int stored, int capacity, int currentPerTick,
                                        int averagePerTick, boolean includesOverhead,
                                        int surchargePercent, @Nullable FluxBand anomalyBand) {
        return lines(stored, capacity, currentPerTick, averagePerTick, includesOverhead,
                surchargePercent, anomalyBand, 0);
    }

    public static List<Component> lines(int stored, int capacity, int currentPerTick,
                                        int averagePerTick, boolean includesOverhead,
                                        int surchargePercent, @Nullable FluxBand anomalyBand,
                                        int passiveDrainFePerTick) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§cEnergy: §r" + String.format("%,d", stored)
                + " / " + String.format("%,d", capacity) + " FE"));
        if (currentPerTick >= 0) {
            lines.add(Component.literal("§eCurrent: §f" + String.format("%,d", currentPerTick) + " FE/t"));
        }
        if (averagePerTick >= 0) {
            lines.add(Component.literal("§e20-tick average: §f"
                    + String.format("%,d", averagePerTick) + " FE/t"));
        }
        if (includesOverhead) {
            lines.add(Component.literal("§820% quantum entanglement overhead included"));
        }
        if (surchargePercent > 0 && anomalyBand != null) {
            lines.add(Component.translatable("gui.quantimium.energy.anomaly_surcharge",
                            Component.translatable("flux.quantimium.band." + anomalyBand.getSerializedName()),
                            surchargePercent)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (passiveDrainFePerTick > 0 && anomalyBand != null) {
            lines.add(Component.translatable("gui.quantimium.energy.anomaly_drain",
                            Component.translatable("flux.quantimium.band." + anomalyBand.getSerializedName()),
                            String.format("%,d", passiveDrainFePerTick))
                    .withStyle(ChatFormatting.GOLD));
        }
        return List.copyOf(lines);
    }
}
