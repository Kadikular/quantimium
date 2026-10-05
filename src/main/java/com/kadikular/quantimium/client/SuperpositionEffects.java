package com.kadikular.quantimium.client;

import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.superposition.Superposition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The particles that go with a pod's effects; the pod's renderer draws the rest. The echo is what is
 * left where a rescued body fell: the shape of a person letting go, rising and scattering.
 */
public final class SuperpositionEffects {

    private SuperpositionEffects() {}

    public static void play(BlockPos pos, int kind) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.getBlockEntity(pos) instanceof SuperpositionPodBlockEntity pod) pod.playEffect(kind);
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        switch (kind) {
            case Superposition.EFFECT_ARRIVE -> {
                for (int i = 0; i < 40; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    double speed = 0.12 + random.nextDouble() * 0.1;
                    level.addParticle(ParticleTypes.END_ROD, x, y + 0.2 + random.nextDouble() * 1.6, z,
                            Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed);
                }
            }
            case Superposition.EFFECT_DEPART, Superposition.EFFECT_UNFOLD -> {
                for (int i = 0; i < 36; i++) {
                    double angle = i / 36.0 * Mth.TWO_PI * 3;
                    double height = i / 36.0 * 1.8;
                    level.addParticle(ParticleTypes.END_ROD, x + Math.cos(angle) * 0.42, y + 0.15 + height,
                            z + Math.sin(angle) * 0.42, 0, 0.01, 0);
                }
            }
            case Superposition.EFFECT_FOLD -> {
                for (int i = 0; i < 50; i++) {
                    level.addParticle(ParticleTypes.REVERSE_PORTAL, x + (random.nextDouble() - 0.5) * 0.6,
                            y + 0.2 + random.nextDouble() * 1.7, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
                }
            }
            case Superposition.EFFECT_ECHO -> {
                // A body's height of light that rises and comes apart.
                for (int i = 0; i < 90; i++) {
                    double height = random.nextDouble() * 1.8;
                    double spread = 0.3 * (1.0 - Math.abs(height - 1.0) * 0.4);
                    level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                            x + (random.nextDouble() - 0.5) * spread, y + height, z + (random.nextDouble() - 0.5) * spread,
                            (random.nextDouble() - 0.5) * 0.04, 0.03 + random.nextDouble() * 0.05,
                            (random.nextDouble() - 0.5) * 0.04);
                }
                level.playLocalSound(x, y + 1, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.6f, false);
                level.playLocalSound(x, y + 1, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.5f, false);
            }
            default -> {
            }
        }
    }
}
