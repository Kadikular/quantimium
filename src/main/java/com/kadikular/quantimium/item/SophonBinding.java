package com.kadikular.quantimium.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Whose body a Sophon holds folded up, and which of their Sophons it is. The id is the Sophon's
 * entry in the {@link com.kadikular.quantimium.superposition.SophonRegistry}, and follows the double
 * from pod to pod as its owner swaps between them.
 */
public record SophonBinding(UUID id, UUID owner, String ownerName) {

    public static final Codec<SophonBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SophonBinding::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(SophonBinding::owner),
            Codec.STRING.fieldOf("owner_name").forGetter(SophonBinding::ownerName)
    ).apply(instance, SophonBinding::new));

    public static final StreamCodec<ByteBuf, SophonBinding> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SophonBinding::id,
            UUIDUtil.STREAM_CODEC, SophonBinding::owner,
            ByteBufCodecs.STRING_UTF8, SophonBinding::ownerName,
            SophonBinding::new);
}
