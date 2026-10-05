package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A personal real-world sighting of the Veiled: where to stand the silhouette, facing the player. */
public record VeiledSightingPayload(int id, double x, double y, double z, float yaw) implements CustomPacketPayload {

    public static final Type<VeiledSightingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "veiled_sighting"));
    public static final StreamCodec<FriendlyByteBuf, VeiledSightingPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.id);
                buffer.writeDouble(payload.x);
                buffer.writeDouble(payload.y);
                buffer.writeDouble(payload.z);
                buffer.writeFloat(payload.yaw);
            },
            buffer -> new VeiledSightingPayload(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
