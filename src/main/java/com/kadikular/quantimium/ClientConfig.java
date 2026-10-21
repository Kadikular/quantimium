package com.kadikular.quantimium;

import net.neoforged.neoforge.common.ModConfigSpec;

/** What a player's screens remember between games: kept in their own config/quantimium-client.toml. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("logistics_terminal");
    }

    public static final ModConfigSpec.BooleanValue TERMINAL_CRAFTING = BUILDER
            .comment("Whether the Logistics Terminal shows its crafting grid.")
            .define("craftingShown", false);
    public static final ModConfigSpec.BooleanValue TERMINAL_BY_NAME = BUILDER
            .comment("Whether the Logistics Terminal sorts by name (else by count).")
            .define("sortByName", false);
    public static final ModConfigSpec.ConfigValue<String> TERMINAL_VIEW = BUILDER
            .comment("What the Logistics Terminal's grid counts: ALL, SPLIT, REAL or CRAFT.")
            .define("view", "SPLIT");

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    /** Sets {@code value} and saves, if the config is loaded (it isn't on a server). */
    public static <T> void set(ModConfigSpec.ConfigValue<T> value, T to) {
        if (!SPEC.isLoaded()) return;
        value.set(to);
        value.save();
    }

    /** {@code value}'s setting, or {@code fallback} before the config is loaded. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }
}
