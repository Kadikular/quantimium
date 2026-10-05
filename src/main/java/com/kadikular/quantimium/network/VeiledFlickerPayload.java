package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The Veiled is close to this player's real world: its lights should stutter, this strongly (0–1). */
public record VeiledFlickerPayload(float strength) implements CustomPacketPayload {

    public static final Type<VeiledFlickerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "veiled_flicker"));
    public static final StreamCodec<FriendlyByteBuf, VeiledFlickerPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeFloat(payload.strength),
            buffer -> new VeiledFlickerPayload(buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
