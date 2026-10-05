package com.kadikular.quantimium.client;

import com.kadikular.quantimium.flux.FluxBand;
import net.minecraft.util.Mth;

/**
 * Client cache of the phased player's local field. Chunk flux is not synced by default, so the
 * server pushes a tiny snapshot while the phase is active.
 */
public final class MirrorAtmosphereClient {

    private static volatile float flux;
    private static volatile float anomaly;
    /** 0 at Low through 1 at Singularity, driven by the hotter of flux and anomaly. */
    private static volatile float intensity;
    /** Brief white/violet wash after a bolt; counts down in client ticks. */
    private static int flashTicks;
    private static int nextBoltIn = 80;

    private MirrorAtmosphereClient() {}

    public static void set(float nextFlux, float nextAnomaly) {
        flux = Math.max(0.0f, nextFlux);
        anomaly = Math.max(0.0f, nextAnomaly);
        intensity = intensityOf(Math.max(flux, anomaly));
    }

    public static void clear() {
        flux = 0.0f;
        anomaly = 0.0f;
        intensity = 0.0f;
        flashTicks = 0;
        nextBoltIn = 80;
    }

    public static float flux() {
        return flux;
    }

    public static float anomaly() {
        return anomaly;
    }

    public static float intensity() {
        return intensity;
    }

    public static boolean flashing() {
        return flashTicks > 0;
    }

    public static float flashStrength() {
        return flashTicks <= 0 ? 0.0f : Mth.clamp(flashTicks / 8.0f, 0.0f, 1.0f);
    }

    public static void beginFlash(int ticks) {
        flashTicks = Math.max(flashTicks, ticks);
    }

    /** Returns true when a bolt should be drawn this tick. */
    public static boolean pollBolt(net.minecraft.util.RandomSource random) {
        if (--nextBoltIn > 0) return false;
        int gap = Mth.floor(Mth.lerp(intensity, 160.0f, 35.0f));
        nextBoltIn = gap + random.nextInt(Math.max(1, gap / 2));
        beginFlash(6 + random.nextInt(5));
        return true;
    }

    public static void tickFlash() {
        if (flashTicks > 0) flashTicks--;
    }

    /** 0 at the bottom of Low up to 1 at Singularity, smooth within each band. */
    public static float intensityOf(float value) {
        FluxBand band = FluxBand.of(value);
        float floor = (float) band.minFlux();
        FluxBand[] bands = FluxBand.values();
        float ceiling = band == FluxBand.SINGULARITY
                ? floor * 10.0f
                : (float) bands[band.ordinal() + 1].minFlux();
        float within = ceiling <= floor ? 1.0f : Mth.clamp((value - floor) / (ceiling - floor), 0.0f, 1.0f);
        return (band.ordinal() + within) / (bands.length - 1);
    }
}
