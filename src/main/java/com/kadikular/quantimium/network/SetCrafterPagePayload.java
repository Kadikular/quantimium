package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetCrafterPagePayload(int containerId, int page) implements CustomPacketPayload {

    public static final Type<SetCrafterPagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "set_crafter_page"));

    public static final StreamCodec<ByteBuf, SetCrafterPagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetCrafterPagePayload::containerId,
            ByteBufCodecs.VAR_INT, SetCrafterPagePayload::page,
            SetCrafterPagePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
