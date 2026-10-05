package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The field round a Field Monitor, sent once a second to a player with its screen open. */
public record FieldMonitorPayload(FieldSurveyPayload survey) implements CustomPacketPayload {

    public static final Type<FieldMonitorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "field_monitor"));

    public static final StreamCodec<FriendlyByteBuf, FieldMonitorPayload> STREAM_CODEC =
            FieldSurveyPayload.STREAM_CODEC.map(FieldMonitorPayload::new, FieldMonitorPayload::survey);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
