package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The client's sighting is over. The server checks the id against the one it handed out. */
public record VeiledSightingEndPayload(int id) implements CustomPacketPayload {

    public static final Type<VeiledSightingEndPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "veiled_sighting_end"));
    public static final StreamCodec<FriendlyByteBuf, VeiledSightingEndPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.id),
            buffer -> new VeiledSightingEndPayload(buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
