package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every flux rift in the viewer's dimension. Both realms get the same list — the real world draws
 * the shadow, the mirror draws the anchor — so phase changes need no resync.
 */
public record FluxRiftSyncPayload(List<Snapshot> rifts) implements CustomPacketPayload {

    public record Snapshot(UUID id, BlockPos anchor, int shapeSeed, int stage, float coherence,
                           boolean draining, boolean stabilised) {}

    public static final Type<FluxRiftSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "flux_rift_sync"));
    public static final StreamCodec<FriendlyByteBuf, FluxRiftSyncPayload> STREAM_CODEC =
            StreamCodec.of(FluxRiftSyncPayload::encode, FluxRiftSyncPayload::decode);

    private static void encode(FriendlyByteBuf buffer, FluxRiftSyncPayload payload) {
        buffer.writeVarInt(payload.rifts.size());
        for (Snapshot rift : payload.rifts) {
            buffer.writeUUID(rift.id);
            buffer.writeBlockPos(rift.anchor);
            buffer.writeInt(rift.shapeSeed);
            buffer.writeVarInt(rift.stage);
            buffer.writeFloat(rift.coherence);
            buffer.writeBoolean(rift.draining);
            buffer.writeBoolean(rift.stabilised);
        }
    }

    private static FluxRiftSyncPayload decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Snapshot> rifts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rifts.add(new Snapshot(buffer.readUUID(), buffer.readBlockPos(), buffer.readInt(),
                    buffer.readVarInt(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean()));
        }
        return new FluxRiftSyncPayload(List.copyOf(rifts));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
