package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.compat.mi.ModernIndustrialization;

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

    /** FE charged per tick of vanilla furnace burn time. */
    public static final int FE_PER_BURN_TICK = 10;

    /** No craft is ever free, however trivial. */
    public static final int MINIMUM_FE = 100;

    private CraftEnergy() {}

    /** Bills a Modern Industrialization recipe from its total EU draw. */
    public static int fromEu(long totalEu) {
        return taxed(totalEu * ModernIndustrialization.fePerEu());
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
