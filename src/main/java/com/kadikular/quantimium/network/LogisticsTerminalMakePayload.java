package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** The open Logistics Terminal's make page: make {@code count} of {@code item} (mB for a fluid) and keep it in storage. */
public record LogisticsTerminalMakePayload(BlockPos pos, ItemResource item, long count) implements CustomPacketPayload {

    public static final Type<LogisticsTerminalMakePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "logistics_terminal_make"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTerminalMakePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LogisticsTerminalMakePayload::pos,
            ItemResource.STREAM_CODEC, LogisticsTerminalMakePayload::item,
            ByteBufCodecs.VAR_LONG, LogisticsTerminalMakePayload::count,
            LogisticsTerminalMakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
