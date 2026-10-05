package com.kadikular.quantimium.client;


import com.kadikular.quantimium.client.renderer.MirrorLightningRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Mirror-only weather: ash motes and Stranger-Things bolts. Persistent flora is server-side.
 */
public final class MirrorAtmosphereEffects {

    private MirrorAtmosphereEffects() {}

    public static void tick(Minecraft minecraft) {
        MirrorLightningRenderer.tick();
        if (!ClientPhaseState.isActive() || minecraft.level == null || minecraft.player == null) {
            MirrorAtmosphereClient.clear();
            return;
        }
        if (!effectsAllowed(minecraft)) return;

        MirrorAtmosphereClient.tickFlash();
        RandomSource random = minecraft.level.getRandom();
        float intensity = MirrorAtmosphereClient.intensity();
        spawnAsh(minecraft.level, minecraft.player, random, intensity);

        if (MirrorAtmosphereClient.pollBolt(random)) {
            MirrorLightningRenderer.strike(minecraft.level, minecraft.player.position(), random);
        }
    }

    /** Skip spawns while paused or a screen is open — catch-up ticks otherwise flood particles. */
    public static boolean effectsAllowed(Minecraft minecraft) {
        return !minecraft.isPaused() && minecraft.screen == null;
    }

    private static void spawnAsh(Level level, LocalPlayer player, RandomSource random, float intensity) {
        int count = 1 + Mth.floor(intensity * 5.0f);
        if (random.nextFloat() > 0.55f + intensity * 0.35f) return;
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 18.0;
            double y = player.getEyeY() + (random.nextDouble() - 0.35) * 10.0;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 18.0;
            level.addParticle(ParticleTypes.WHITE_ASH, x, y, z,
                    (random.nextDouble() - 0.5) * 0.02,
                    -0.02 - random.nextDouble() * 0.03,
                    (random.nextDouble() - 0.5) * 0.02);
            if (intensity > 0.45f && random.nextFloat() < intensity * 0.35f) {
                level.addParticle(ParticleTypes.ASH, x, y, z,
                        (random.nextDouble() - 0.5) * 0.01,
                        -0.01 - random.nextDouble() * 0.02,
                        (random.nextDouble() - 0.5) * 0.01);
            }
        }
    }
}
