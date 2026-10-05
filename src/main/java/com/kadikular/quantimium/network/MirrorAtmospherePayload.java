package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Local flux/anomaly snapshot for Mirror Phase atmosphere. Owner-only, a few times a second. */
public record MirrorAtmospherePayload(float flux, float anomaly) implements CustomPacketPayload {
    public static final Type<MirrorAtmospherePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_atmosphere"));
    public static final StreamCodec<FriendlyByteBuf, MirrorAtmospherePayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeFloat(payload.flux);
                buf.writeFloat(payload.anomaly);
            }, buf -> new MirrorAtmospherePayload(buf.readFloat(), buf.readFloat()));

    public static MirrorAtmospherePayload clear() {
        return new MirrorAtmospherePayload(0.0f, 0.0f);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
