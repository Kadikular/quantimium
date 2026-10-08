package com.kadikular.quantimium.util;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** How a machine listens to redstone: not at all, running only with a signal, or only without one. */
public enum RedstoneMode {
    IGNORED(Items.GUNPOWDER),
    WITH_SIGNAL(Items.REDSTONE_TORCH),
    WITHOUT_SIGNAL(Items.REDSTONE);

    /** The item its button shows. */
    public final Item icon;

    RedstoneMode(Item icon) {
        this.icon = icon;
    }

    /** Whether a machine at {@code pos} runs, given the signal reaching it. */
    public boolean allows(Level level, BlockPos pos) {
        return switch (this) {
            case IGNORED -> true;
            case WITH_SIGNAL -> level.hasNeighborSignal(pos);
            case WITHOUT_SIGNAL -> !level.hasNeighborSignal(pos);
        };
    }

    public RedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static RedstoneMode byOrdinal(int ordinal) {
        return values()[Math.clamp(ordinal, 0, values().length - 1)];
    }

    public Component label() {
        return Component.translatable("gui.quantimium.redstone." + name().toLowerCase(Locale.ROOT));
    }

    public Component tip() {
        return Component.translatable("gui.quantimium.redstone." + name().toLowerCase(Locale.ROOT) + ".tip");
    }
}
