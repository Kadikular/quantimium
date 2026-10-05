package com.kadikular.quantimium.compat.jade;

import com.kadikular.quantimium.util.NbtCompat;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.FieldTrend;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The field and the block's readout, for Quantimium blocks: the client half, laying out what
 * {@link FieldData} read on the server (the client has no field of its own). Jade wants the two halves
 * apart since 1.21.6.
 */
enum FieldProvider implements IBlockComponentProvider {
    INSTANCE;

    static final Identifier UID = Identifier.fromNamespaceAndPath(Quantimium.MODID, "field");
    static final String KEY = "quantimium_field";

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY)) return;
        CompoundTag field = data.getCompoundOrEmpty(KEY);
        double flux = field.getDoubleOr("flux", 0.0);
        double anomaly = field.getDoubleOr("anomaly", 0.0);
        double settling = field.getDoubleOr("settling", 0.0);
        tooltip.add(Component.translatable("jade.quantimium.flux", FluxMeterReadout.flux(flux),
                FieldTrend.describe(flux, settling)).withStyle(ChatFormatting.AQUA));
        MutableComponent anomalyLine = Component.translatable("jade.quantimium.anomaly", FluxMeterReadout.flux(anomaly),
                Component.translatable("flux.quantimium.band." + FluxBand.of(anomaly).getSerializedName()));
        double load = field.getDoubleOr("load", 0.0);
        if (load >= 0.0) {
            anomalyLine.append(Component.translatable(load <= 1.0 ? "jade.quantimium.contained" : "jade.quantimium.overloaded",
                    Math.round(load * 100.0)));
        }
        tooltip.add(anomalyLine.withStyle(ChatFormatting.LIGHT_PURPLE));
        int surcharge = field.getIntOr("surcharge", 0);
        int leak = field.getIntOr("leak", 0);
        if (surcharge > 0 || leak > 0) {
            tooltip.add(Component.translatable("jade.quantimium.tax", surcharge, leak).withStyle(ChatFormatting.GOLD));
        }
        if (field.contains("readout")) {
            Component line = NbtCompat.componentFromJson(field.getStringOr("readout", ""), accessor.getLevel().registryAccess());
            if (line != null) tooltip.add(line);
        }
    }
}
