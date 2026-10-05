package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** One arc of lightning out of a flux rift. Pure spectacle; the damage has already happened. */
public record FluxRiftStrikePayload(Vec3 from, Vec3 to) implements CustomPacketPayload {

    public static final Type<FluxRiftStrikePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "flux_rift_strike"));
    public static final StreamCodec<FriendlyByteBuf, FluxRiftStrikePayload> STREAM_CODEC =
            StreamCodec.of(FluxRiftStrikePayload::encode, FluxRiftStrikePayload::decode);

    private static void encode(FriendlyByteBuf buffer, FluxRiftStrikePayload payload) {
        Vec3.STREAM_CODEC.encode(buffer, payload.from);
        Vec3.STREAM_CODEC.encode(buffer, payload.to);
    }

    private static FluxRiftStrikePayload decode(FriendlyByteBuf buffer) {
        return new FluxRiftStrikePayload(Vec3.STREAM_CODEC.decode(buffer), Vec3.STREAM_CODEC.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
