package com.kadikular.quantimium.compat.jade;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.laser.Acceleration;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Any machine an Acceleration Lens is speeding up says so: "Accelerated 8x", or, held back by the tick budget,
 * "Accelerated 5x of 8x (tick budget)". The server half reads what the lens last did; the client half shows it.
 */
final class AccelerationProvider {
    private AccelerationProvider() {}

    static final Identifier UID = Identifier.fromNamespaceAndPath(Quantimium.MODID, "accelerated");
    private static final String KEY = "quantimium_accelerated";

    enum Data implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            Acceleration.Shown shown = Acceleration.shown(accessor.getLevel(), accessor.getPosition());
            if (shown == null) return;
            CompoundTag tag = new CompoundTag();
            tag.putInt("at", shown.multiplier());
            tag.putInt("asked", shown.asked());
            tag.putBoolean("budgeted", shown.budgeted());
            data.put(KEY, tag);
        }
    }

    enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(KEY)) return;
            CompoundTag tag = data.getCompoundOrEmpty(KEY);
            int at = tag.getIntOr("at", 1);
            Component line = tag.getBooleanOr("budgeted", false)
                    ? Component.translatable("jade.quantimium.accelerated.budget", at, tag.getIntOr("asked", at))
                    : Component.translatable("jade.quantimium.accelerated", at);
            tooltip.add(line.copy().withStyle(ChatFormatting.AQUA));
        }
    }
}
