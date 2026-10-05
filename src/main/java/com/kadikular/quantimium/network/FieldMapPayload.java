package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The field for a world map (JourneyMap): every chunk holding one within
 * {@link com.kadikular.quantimium.flux.FieldMapSync#RADIUS} chunks of the player, in their dimension.
 * Each chunk is its position ({@link net.minecraft.world.level.ChunkPos#toLong}), its flux and
 * anomaly, the load of containment over it (-1 with none) and whether it is contained. A chunk not
 * listed has no field.
 */
public record FieldMapPayload(long[] chunks, float[] flux, float[] anomaly, float[] load, boolean[] contained)
        implements CustomPacketPayload {

    public static final Type<FieldMapPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "field_map"));

    public static final StreamCodec<FriendlyByteBuf, FieldMapPayload> STREAM_CODEC =
            StreamCodec.of(FieldMapPayload::write, FieldMapPayload::read);

    private static void write(FriendlyByteBuf buf, FieldMapPayload payload) {
        buf.writeVarInt(payload.chunks.length);
        for (int i = 0; i < payload.chunks.length; i++) {
            buf.writeLong(payload.chunks[i]);
            buf.writeFloat(payload.flux[i]);
            buf.writeFloat(payload.anomaly[i]);
            buf.writeFloat(payload.load[i]);
            buf.writeBoolean(payload.contained[i]);
        }
    }

    private static FieldMapPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        long[] chunks = new long[count];
        float[] flux = new float[count];
        float[] anomaly = new float[count];
        float[] load = new float[count];
        boolean[] contained = new boolean[count];
        for (int i = 0; i < count; i++) {
            chunks[i] = buf.readLong();
            flux[i] = buf.readFloat();
            anomaly[i] = buf.readFloat();
            load[i] = buf.readFloat();
            contained[i] = buf.readBoolean();
        }
        return new FieldMapPayload(chunks, flux, anomaly, load, contained);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
