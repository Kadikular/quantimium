package com.kadikular.quantimium.init;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import java.util.function.Predicate;
import com.mojang.serialization.Codec;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.ChunkFlux;
import com.kadikular.quantimium.flux.MirrorFloraData;
import com.kadikular.quantimium.phase.FluxRiftData;
import com.kadikular.quantimium.phase.MirrorPhaseState;
import com.kadikular.quantimium.phase.VeiledManager;
import com.kadikular.quantimium.phase.WorldRiftData;
import com.kadikular.quantimium.superposition.SophonRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Quantimium.MODID);

    /**
     * Per-chunk flux / anomaly. Only written to disk while non-empty so cold chunks stay lean.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ChunkFlux>> CHUNK_FLUX =
            ATTACHMENT_TYPES.register("chunk_flux", () -> AttachmentType.builder(ChunkFlux::new)
                    .serialize(codecSerializer(ChunkFlux.CODEC, data -> !data.isEmpty()))
                    .build());

    /** Persistent render-only mirror flora; no flora block states are placed in the level. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MirrorFloraData>> MIRROR_FLORA =
            ATTACHMENT_TYPES.register("mirror_flora", () -> AttachmentType.builder(MirrorFloraData::new)
                    .serialize(codecSerializer(MirrorFloraData.CODEC, data -> !data.isEmpty()))
                    .build());

    /** Player-session state is deliberately not serialized: logout/death always exits safely. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MirrorPhaseState>> MIRROR_PHASE =
            ATTACHMENT_TYPES.register("mirror_phase",
                    () -> AttachmentType.builder(MirrorPhaseState::inactive).build());

    /** Dimension-owned tears. Session-only; not written to disk. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WorldRiftData>> WORLD_RIFTS =
            ATTACHMENT_TYPES.register("world_rifts",
                    () -> AttachmentType.builder(WorldRiftData::new).build());

    /**
     * Dimension-owned flux rifts. Saved, unlike tears: a wound that healed on relog would make the
     * whole mechanic optional.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FluxRiftData>> FLUX_RIFTS =
            ATTACHMENT_TYPES.register("flux_rifts", () -> AttachmentType.builder(FluxRiftData::new)
                    .serialize(codecSerializer(FluxRiftData.CODEC, data -> !data.isEmpty()))
                    .build());

    /** Containment Halls holding an Veiled, so the one-per-area rule can see held ones too. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VeiledManager.HeldHalls>> HELD_VEILED =
            ATTACHMENT_TYPES.register("held_veiled", () -> AttachmentType.builder(VeiledManager.HeldHalls::new)
                    .serialize(codecSerializer(VeiledManager.HeldHalls.CODEC, halls -> !halls.isEmpty()))
                    .build());

    /** Every Sophon and where its double waits, kept on the Overworld. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SophonRegistry>> SOPHONS =
            ATTACHMENT_TYPES.register("sophons", () -> AttachmentType.builder(SophonRegistry::new)
                    .serialize(codecSerializer(SophonRegistry.CODEC, registry -> !registry.isEmpty()))
                    .build());

    private static final String VALUE_KEY = "value";

    /**
     * Persists an attachment through a codec under one key, and only while {@code shouldSerialize} holds.
     * Attachments whose codec is not a record sit under "value". (On 1.21.1 they were the attachment's own
     * tag; 1.21.1 worlds aren't carried over to 26.1.)
     */
    private static <T> IAttachmentSerializer<T> codecSerializer(Codec<T> codec, Predicate<? super T> shouldSerialize) {
        return new IAttachmentSerializer<>() {
            @Override
            public T read(IAttachmentHolder holder, ValueInput input) {
                return input.read(VALUE_KEY, codec)
                        .orElseThrow(() -> new IllegalStateException("Unreadable attachment data"));
            }

            @Override
            public boolean write(T attachment, ValueOutput output) {
                if (!shouldSerialize.test(attachment)) return false;
                output.store(VALUE_KEY, codec, attachment);
                return true;
            }
        };
    }

    private ModAttachments() {}

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
