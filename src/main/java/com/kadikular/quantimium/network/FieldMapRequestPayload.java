package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** From a client with a world map mod: whether to send it the field for its map. */
public record FieldMapRequestPayload(boolean wanted) implements CustomPacketPayload {

    public static final Type<FieldMapRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "field_map_request"));

    public static final StreamCodec<ByteBuf, FieldMapRequestPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(FieldMapRequestPayload::new, FieldMapRequestPayload::wanted);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
