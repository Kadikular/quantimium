package com.kadikular.quantimium.compat.mi;

/**
 * What Quantimium reads of Modern Industrialization's settings, by reflection so MI needn't be on the classpath.
 * The Quantum Crafter prices MI recipes and the Quantum Simulator fills MI machines' buffers at the same rate.
 */
public final class ModernIndustrialization {

    /** MI's default {@code forgeEnergyPerEu}, used when MI is absent or its config is not loaded yet. */
    public static final int DEFAULT_FE_PER_EU = 10;

    private static final String SERVER_CONFIG = "aztech.modern_industrialization.config.MIServerConfig";

    private static volatile int cachedFePerEu = -1;

    private ModernIndustrialization() {}

    /**
     * FE worth one EU: MI's {@code forgeEnergyPerEu}. Kept once read; until then (MI missing, or its config not
     * loaded yet) the default, and asked again next time.
     */
    public static int fePerEu() {
        int cached = cachedFePerEu;
        if (cached > 0) return cached;
        try {
            Class<?> config = Class.forName(SERVER_CONFIG);
            Object instance = config.getField("INSTANCE").get(null);
            Object value = config.getField("forgeEnergyPerEu").get(instance);
            int fePerEu = ((Number) value.getClass().getMethod("getAsInt").invoke(value)).intValue();
            if (fePerEu > 0) {
                cachedFePerEu = fePerEu;
                return fePerEu;
            }
        } catch (Throwable ignored) {
            // MI missing, or its config not loaded yet: asked again on a later call.
        }
        return DEFAULT_FE_PER_EU;
    }
}
