package com.kadikular.quantimium.compat.mi;

import java.lang.reflect.InvocationTargetException;

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
     * FE worth one EU: MI's {@code forgeEnergyPerEu}. Asked again only while MI's config isn't loaded yet; once read,
     * or if MI is missing or no longer has the setting where it's looked for, the answer (or the default) is kept, so
     * a failing lookup isn't repeated on the per-block paths that ask.
     */
    public static int fePerEu() {
        int cached = cachedFePerEu;
        if (cached > 0) return cached;
        try {
            Class<?> config = Class.forName(SERVER_CONFIG);
            Object instance = config.getField("INSTANCE").get(null);
            if (instance == null) return DEFAULT_FE_PER_EU;
            Object value = config.getField("forgeEnergyPerEu").get(instance);
            int fePerEu = ((Number) value.getClass().getMethod("getAsInt").invoke(value)).intValue();
            return cachedFePerEu = fePerEu > 0 ? fePerEu : DEFAULT_FE_PER_EU;
        } catch (InvocationTargetException notLoaded) {
            // The config value refuses to be read before MI's config loads: asked again on a later call.
            return DEFAULT_FE_PER_EU;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return cachedFePerEu = DEFAULT_FE_PER_EU;
        }
    }
}
