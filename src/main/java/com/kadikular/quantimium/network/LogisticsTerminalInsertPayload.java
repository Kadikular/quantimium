package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A click on the open Logistics Terminal's grid with something on the cursor: all of it (or with {@code one}, one) into the network. */
public record LogisticsTerminalInsertPayload(BlockPos pos, boolean one) implements CustomPacketPayload {

    public static final Type<LogisticsTerminalInsertPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "logistics_terminal_insert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTerminalInsertPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LogisticsTerminalInsertPayload::pos,
            ByteBufCodecs.BOOL, LogisticsTerminalInsertPayload::one,
            LogisticsTerminalInsertPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
