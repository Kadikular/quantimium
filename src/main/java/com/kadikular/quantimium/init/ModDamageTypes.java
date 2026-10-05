package com.kadikular.quantimium.init;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageType;

/** Datapack damage types; the JSON lives under {@code data/quantimium/damage_type}. */
public final class ModDamageTypes {

    public static final ResourceKey<DamageType> FLUX_RIFT = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "flux_rift"));

    /** The Veiled's strike. No attacking entity, so mirror-phase isolation lets it land. */
    public static final ResourceKey<DamageType> VEILED = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "veiled"));

    private ModDamageTypes() {}
}
