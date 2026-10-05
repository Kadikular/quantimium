package com.kadikular.quantimium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Lights stuttering while the Veiled's true position is near. The server pulses a strength once
 * a second; between pulses this rolls short, irregular dips in block light — torches, lamps, glowing
 * machines — that the lightmap reads through {@link #dim()}. Sky light is left alone, so it reads as
 * the lamps faltering, not the world going dark.
 */
public final class VeiledFlickerClient {

    /** Taken off vanilla's 1.4 block-light factor at a full dip: lamps drop to about a seventh. */
    private static final float MAX_DIM = 1.2f;
    /** A pulse keeps the flicker going this long, so a missed packet does not stop it dead. */
    private static final int PULSE_TICKS = 30;

    private static float strength;
    private static int remaining;
    private static int dipTicks;
    private static float dipDepth;
    private static float current;
    private static final RandomSource RANDOM = RandomSource.create();

    private VeiledFlickerClient() {}

    public static void pulse(float value) {
        strength = Mth.clamp(value, 0.0f, 1.0f);
        remaining = PULSE_TICKS;
    }

    public static void clear() {
        strength = 0.0f;
        remaining = 0;
        dipTicks = 0;
        current = 0.0f;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        if (remaining > 0) remaining--;
        float active = remaining > 0 && !ClientPhaseState.isActive() ? strength : 0.0f;
        RandomSource random = RANDOM;
        if (dipTicks > 0) {
            dipTicks--;
        } else if (active > 0.0f && random.nextFloat() < 0.08f + 0.25f * active) {
            // A dip: short and sharp, deeper the closer it is, sometimes a double stutter.
            dipTicks = 1 + random.nextInt(4);
            dipDepth = active * (0.5f + 0.5f * random.nextFloat());
        }
        float target = dipTicks > 0 ? dipDepth : 0.0f;
        current += (target - current) * 0.6f;
    }

    /** How much to take off block light this frame. */
    public static float dim() {
        return current * MAX_DIM;
    }
}
