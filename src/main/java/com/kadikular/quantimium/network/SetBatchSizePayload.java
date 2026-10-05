package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetBatchSizePayload(BlockPos pos, int size) implements CustomPacketPayload {
    public static final Type<SetBatchSizePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "set_batch_size"));
    public static final StreamCodec<FriendlyByteBuf, SetBatchSizePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetBatchSizePayload::pos,
            ByteBufCodecs.VAR_INT, SetBatchSizePayload::size,
            SetBatchSizePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
