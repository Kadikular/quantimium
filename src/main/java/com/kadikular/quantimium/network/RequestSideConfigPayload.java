package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestSideConfigPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<RequestSideConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "request_side_config"));
    public static final StreamCodec<FriendlyByteBuf, RequestSideConfigPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, RequestSideConfigPayload::pos, RequestSideConfigPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
