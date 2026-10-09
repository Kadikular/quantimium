package com.kadikular.quantimium.laser;

import net.minecraft.network.chat.Component;

/**
 * How a mirror or splitter listens to redstone: not at all, passing light only with a signal, only
 * without one, or for one pulse: a signal's rising edge lets light through until one action down the
 * beam is done (a block cut, a hit, an item carried, a block placed), or {@link #PULSE_TICKS} pass.
 */
public enum RelayMode {
    IGNORED, WITH_SIGNAL, WITHOUT_SIGNAL, PULSE;

    /** How long a pulse lets plain light through, with no action to finish. */
    public static final int PULSE_TICKS = 40;

    public RelayMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static RelayMode byOrdinal(int ordinal) {
        return values()[Math.floorMod(ordinal, values().length)];
    }

    public Component label() {
        return Component.translatable("message.quantimium.relay_mode." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
