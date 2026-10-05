package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.FluxSources;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The field around a player who can see it (in the mirror, or wearing a Mirror Lens): each chunk's
 * flux and anomaly, whether it is contained, how loaded its containment is (-1 with none) and the flux it
 * is settling at (NaN if not yet known), in a square {@code radius} chunks out from
 * ({@code centreX}, {@code centreZ}), row by row in z then x; and the blocks feeding or draining it
 * nearby. A radius of -1 clears it: the player has stopped being able to see.
 */
public record FieldSurveyPayload(int centreX, int centreZ, int radius, float[] flux, float[] anomaly,
                                 boolean[] contained, float[] load, float[] settling, List<FluxSources.Source> sources)
        implements CustomPacketPayload {

    public static final Type<FieldSurveyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "field_survey"));

    public static final StreamCodec<FriendlyByteBuf, FieldSurveyPayload> STREAM_CODEC =
            StreamCodec.of(FieldSurveyPayload::write, FieldSurveyPayload::read);

    public static FieldSurveyPayload clear() {
        return new FieldSurveyPayload(0, 0, -1, new float[0], new float[0], new boolean[0], new float[0], new float[0], List.of());
    }

    public boolean isClear() {
        return radius < 0;
    }

    /** Index of chunk ({@code dx}, {@code dz}) from the centre in the arrays. */
    public int index(int dx, int dz) {
        int side = 2 * radius + 1;
        return (dz + radius) * side + (dx + radius);
    }

    private static void write(FriendlyByteBuf buf, FieldSurveyPayload payload) {
        buf.writeVarInt(payload.centreX);
        buf.writeVarInt(payload.centreZ);
        buf.writeByte(payload.radius);
        for (int i = 0; i < payload.flux.length; i++) {
            buf.writeFloat(payload.flux[i]);
            buf.writeFloat(payload.anomaly[i]);
            buf.writeBoolean(payload.contained[i]);
            buf.writeFloat(payload.load[i]);
            buf.writeFloat(payload.settling[i]);
        }
        buf.writeVarInt(payload.sources.size());
        for (FluxSources.Source source : payload.sources) {
            buf.writeBlockPos(source.pos());
            buf.writeFloat(source.flux());
            buf.writeFloat(source.anomaly());
            buf.writeBoolean(source.sink());
        }
    }

    private static FieldSurveyPayload read(FriendlyByteBuf buf) {
        int centreX = buf.readVarInt();
        int centreZ = buf.readVarInt();
        int radius = buf.readByte();
        int cells = radius < 0 ? 0 : (2 * radius + 1) * (2 * radius + 1);
        float[] flux = new float[cells];
        float[] anomaly = new float[cells];
        boolean[] contained = new boolean[cells];
        float[] load = new float[cells];
        float[] settling = new float[cells];
        for (int i = 0; i < cells; i++) {
            flux[i] = buf.readFloat();
            anomaly[i] = buf.readFloat();
            contained[i] = buf.readBoolean();
            load[i] = buf.readFloat();
            settling[i] = buf.readFloat();
        }
        int count = buf.readVarInt();
        List<FluxSources.Source> sources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sources.add(new FluxSources.Source(buf.readBlockPos(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
        return new FieldSurveyPayload(centreX, centreZ, radius, flux, anomaly, contained, load, settling, sources);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
