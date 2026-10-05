package com.kadikular.quantimium.init;

import net.minecraft.resources.ResourceKey;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.entity.FieldDouble;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.entity.Veiled;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Quantimium.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MirrorEndermite>> MIRROR_ENDERMITE =
            ENTITY_TYPES.register("mirror_endermite", () -> EntityType.Builder.of(
                            MirrorEndermite::new, MobCategory.MONSTER)
                    .sized(0.4F, 0.3F)
                    .eyeHeight(0.13F)
                    .passengerAttachments(0.2375F)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_endermite"))));

    /** The Veiled (placeholder name), Veiled form. Three blocks tall; see VEILED_BRIEF.html. */
    public static final DeferredHolder<EntityType<?>, EntityType<Veiled>> VEILED =
            ENTITY_TYPES.register("veiled", () -> EntityType.Builder.of(
                            Veiled::new, MobCategory.MONSTER)
                    .sized(0.9F, 3.0F)
                    .eyeHeight(2.55F)
                    .clientTrackingRange(16)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(Quantimium.MODID, "veiled"))));

    /** A body left standing where its owner tethered away from. */
    public static final DeferredHolder<EntityType<?>, EntityType<FieldDouble>> FIELD_DOUBLE =
            ENTITY_TYPES.register("field_double", () -> EntityType.Builder.of(FieldDouble::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(Quantimium.MODID, "field_double"))));

    private ModEntities() {}

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        eventBus.addListener(ModEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(MIRROR_ENDERMITE.get(), MirrorEndermite.createAttributes().build());
        event.put(VEILED.get(), Veiled.createAttributes().build());
        event.put(FIELD_DOUBLE.get(), FieldDouble.createAttributes().build());
    }
}
