package com.kadikular.quantimium.init;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {

    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Quantimium.MODID);

    /** The field made visible (Mirror Lens, or the mirror): plumes, motes, threads. Tinted per use. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIELD_GLOW =
            PARTICLES.register("field_glow", () -> new SimpleParticleType(true));

    /** A soft disc for the field's haze, anomaly's ink and the glow of a plume. Tinted per use. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIELD_SOFT =
            PARTICLES.register("field_soft", () -> new SimpleParticleType(true));

    private ModParticles() {}

    public static void register(IEventBus eventBus) {
        PARTICLES.register(eventBus);
    }
}
