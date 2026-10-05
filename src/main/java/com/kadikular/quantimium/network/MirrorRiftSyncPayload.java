package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.phase.MirrorRiftKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Visible tears for one player after filtering by phase and prior use. */
public record MirrorRiftSyncPayload(List<Snapshot> rifts) implements CustomPacketPayload {

    public record Snapshot(UUID id, MirrorRiftKind kind, BlockPos anchor, Direction facing,
                           int shapeSeed, boolean opening) {}

    public static final Type<MirrorRiftSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_rift_sync"));
    public static final StreamCodec<FriendlyByteBuf, MirrorRiftSyncPayload> STREAM_CODEC =
            StreamCodec.of(MirrorRiftSyncPayload::encode, MirrorRiftSyncPayload::decode);

    public static MirrorRiftSyncPayload empty() {
        return new MirrorRiftSyncPayload(List.of());
    }

    private static void encode(FriendlyByteBuf buffer, MirrorRiftSyncPayload payload) {
        buffer.writeVarInt(payload.rifts.size());
        for (Snapshot rift : payload.rifts) {
            buffer.writeUUID(rift.id);
            buffer.writeEnum(rift.kind);
            buffer.writeBlockPos(rift.anchor);
            buffer.writeEnum(rift.facing);
            buffer.writeInt(rift.shapeSeed);
            buffer.writeBoolean(rift.opening);
        }
    }

    private static MirrorRiftSyncPayload decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Snapshot> rifts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rifts.add(new Snapshot(
                    buffer.readUUID(),
                    buffer.readEnum(MirrorRiftKind.class),
                    buffer.readBlockPos(),
                    buffer.readEnum(Direction.class),
                    buffer.readInt(),
                    buffer.readBoolean()));
        }
        return new MirrorRiftSyncPayload(List.copyOf(rifts));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
