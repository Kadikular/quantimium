package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** One player's overlay flag, sent to that player and to anyone tracking them. */
public record MirrorPhaseSyncPayload(UUID player, boolean active) implements CustomPacketPayload {
    public static final Type<MirrorPhaseSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_phase_sync"));
    public static final StreamCodec<FriendlyByteBuf, MirrorPhaseSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeUUID(payload.player);
                buf.writeBoolean(payload.active);
            }, buf -> new MirrorPhaseSyncPayload(buf.readUUID(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
