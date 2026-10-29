package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.laser.SpectroscopeReading;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** What a Spectroscope read, to the player who used it: their screen opens on it. */
public record SpectroscopePayload(SpectroscopeReading reading) implements CustomPacketPayload {

    public static final Type<SpectroscopePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "spectroscope"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpectroscopePayload> STREAM_CODEC =
            SpectroscopeReading.STREAM_CODEC.map(SpectroscopePayload::new, SpectroscopePayload::reading);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
