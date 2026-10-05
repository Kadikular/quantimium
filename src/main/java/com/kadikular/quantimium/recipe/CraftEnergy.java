package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.Config;

/**
 * Energy pricing for Quantum Crafter recipes.
 *
 * <p>Skipping the machine, the fuel and the wait is worth paying for: every recipe is billed at what
 * the real process would burn, times {@link com.kadikular.quantimium.Config#instantCraftMultiplier()}.
 * Modern Industrialization recipes are converted with MI's own {@code forgeEnergyPerEu} server
 * config so the two economies agree.
 */
public final class CraftEnergy {

    /** MI's default {@code forgeEnergyPerEu}, used when MI is absent or its config is not loaded. */
    public static final int DEFAULT_FE_PER_EU = 10;

    /** FE charged per tick of vanilla furnace burn time. */
    public static final int FE_PER_BURN_TICK = 10;

    /** No craft is ever free, however trivial. */
    public static final int MINIMUM_FE = 100;

    private static final String MI_CONFIG = "aztech.modern_industrialization.config.MIServerConfig";

    private static int cachedFePerEu = -1;

    private CraftEnergy() {}

    /** FE value of one EU, read live from MI once its config is available. */
    public static int fePerEu() {
        if (cachedFePerEu > 0) return cachedFePerEu;
        try {
            Class<?> config = Class.forName(MI_CONFIG);
            Object instance = config.getField("INSTANCE").get(null);
            Object value = config.getField("forgeEnergyPerEu").get(instance);
            int fePerEu = ((Number) value.getClass().getMethod("getAsInt").invoke(value)).intValue();
            if (fePerEu > 0) {
                cachedFePerEu = fePerEu;
                return fePerEu;
            }
        } catch (Throwable ignored) {
            // MI missing, or config not loaded yet — retry on a later call.
        }
        return DEFAULT_FE_PER_EU;
    }

    /** Bills a Modern Industrialization recipe from its total EU draw. */
    public static int fromEu(long totalEu) {
        return taxed(totalEu * fePerEu());
    }

    /** Bills a recipe from how long a furnace would have burned for it. */
    public static int fromBurnTicks(long ticks) {
        return taxed(ticks * FE_PER_BURN_TICK);
    }

    /** Bills a recipe that costs no fuel in the world, such as a crafting table craft. */
    public static int flat(long baseFe) {
        return taxed(baseFe);
    }

    private static int taxed(long baseFe) {
        long total = Math.max(0, baseFe) * Config.instantCraftMultiplier();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(MINIMUM_FE, total));
    }
}
