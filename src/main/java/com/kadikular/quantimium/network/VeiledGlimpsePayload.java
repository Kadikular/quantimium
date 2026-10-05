package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A glimpse at the edge of this player's view: gone the moment they turn to it. Nothing is reported back. */
public record VeiledGlimpsePayload(double x, double y, double z) implements CustomPacketPayload {

    public static final Type<VeiledGlimpsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "veiled_glimpse"));
    public static final StreamCodec<FriendlyByteBuf, VeiledGlimpsePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeDouble(payload.x);
                buffer.writeDouble(payload.y);
                buffer.writeDouble(payload.z);
            },
            buffer -> new VeiledGlimpsePayload(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
