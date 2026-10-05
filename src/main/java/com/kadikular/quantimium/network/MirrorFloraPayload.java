package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.MirrorFloraData;
import com.kadikular.quantimium.init.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/** Complete nearby flora snapshot for one phased player. */
public record MirrorFloraPayload(List<ChunkSnapshot> chunks) implements CustomPacketPayload {

    public record ChunkSnapshot(long chunkPos, List<MirrorFloraData.Entry> entries) {}

    private static final int RADIUS = 8;
    public static final Type<MirrorFloraPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_flora"));
    public static final StreamCodec<FriendlyByteBuf, MirrorFloraPayload> STREAM_CODEC =
            StreamCodec.of(MirrorFloraPayload::encode, MirrorFloraPayload::decode);

    public static MirrorFloraPayload around(ServerPlayer player) {
        ServerLevel level = player.level();
        ChunkPos center = player.chunkPosition();
        List<ChunkSnapshot> snapshots = new ArrayList<>();
        for (int chunkX = center.x() - RADIUS; chunkX <= center.x() + RADIUS; chunkX++) {
            for (int chunkZ = center.z() - RADIUS; chunkZ <= center.z() + RADIUS; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                MirrorFloraData flora = chunk.getExistingData(ModAttachments.MIRROR_FLORA)
                        .orElse(null);
                snapshots.add(new ChunkSnapshot(chunk.getPos().pack(),
                        flora == null ? List.of() : List.copyOf(flora.entries())));
            }
        }
        return new MirrorFloraPayload(List.copyOf(snapshots));
    }

    public static MirrorFloraPayload clear() {
        return new MirrorFloraPayload(List.of());
    }

    private static void encode(FriendlyByteBuf buffer, MirrorFloraPayload payload) {
        buffer.writeVarInt(payload.chunks.size());
        for (ChunkSnapshot chunk : payload.chunks) {
            buffer.writeLong(chunk.chunkPos);
            buffer.writeVarInt(chunk.entries.size());
            for (MirrorFloraData.Entry entry : chunk.entries) {
                buffer.writeLong(entry.packedPos());
                buffer.writeByte(entry.kind().ordinal());
                buffer.writeByte(entry.faces());
            }
        }
    }

    private static MirrorFloraPayload decode(FriendlyByteBuf buffer) {
        int chunkCount = buffer.readVarInt();
        List<ChunkSnapshot> chunks = new ArrayList<>(chunkCount);
        MirrorFloraData.Kind[] kinds = MirrorFloraData.Kind.values();
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            long chunkPos = buffer.readLong();
            int entryCount = buffer.readVarInt();
            List<MirrorFloraData.Entry> entries = new ArrayList<>(entryCount);
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                long packedPos = buffer.readLong();
                int kind = buffer.readUnsignedByte();
                int faces = buffer.readUnsignedByte();
                if (kind < kinds.length) {
                    entries.add(new MirrorFloraData.Entry(packedPos, kinds[kind], faces));
                }
            }
            chunks.add(new ChunkSnapshot(chunkPos, List.copyOf(entries)));
        }
        return new MirrorFloraPayload(List.copyOf(chunks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
