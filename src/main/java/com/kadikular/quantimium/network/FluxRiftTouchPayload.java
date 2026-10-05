package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** The player swung at a flux rift. The server checks reach and sight before anything happens. */
public record FluxRiftTouchPayload(UUID rift) implements CustomPacketPayload {

    public static final Type<FluxRiftTouchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "flux_rift_touch"));
    public static final StreamCodec<FriendlyByteBuf, FluxRiftTouchPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUUID(payload.rift),
            buffer -> new FluxRiftTouchPayload(buffer.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
