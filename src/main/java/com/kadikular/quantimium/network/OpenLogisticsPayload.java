package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The logistics key was pressed: open the network a pod's Logistics module is on, if one holds one of the player's doubles. */
public record OpenLogisticsPayload() implements CustomPacketPayload {

    public static final OpenLogisticsPayload INSTANCE = new OpenLogisticsPayload();

    public static final Type<OpenLogisticsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "open_logistics"));

    public static final StreamCodec<ByteBuf, OpenLogisticsPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
