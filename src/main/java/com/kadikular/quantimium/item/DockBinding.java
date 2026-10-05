package com.kadikular.quantimium.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * An item's entanglement with an Entangled Dock: where the dock is, and the binding it made. The id
 * is fresh each time a dock binds, so an item from an earlier binding (the dock was rebound, emptied,
 * or decohered) no longer matches, and the dock leaves it be.
 */
public record DockBinding(GlobalPos dock, UUID id) {

    public static final Codec<DockBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("dock").forGetter(DockBinding::dock),
            UUIDUtil.CODEC.fieldOf("id").forGetter(DockBinding::id)
    ).apply(instance, DockBinding::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DockBinding> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, DockBinding::dock,
            UUIDUtil.STREAM_CODEC, DockBinding::id,
            DockBinding::new);
}
