package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Shift and the wheel down over an item in the open Logistics Terminal: one of it from the inventory into the network. */
public record LogisticsTerminalDepositPayload(BlockPos pos, ItemResource item) implements CustomPacketPayload {

    public static final Type<LogisticsTerminalDepositPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "logistics_terminal_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTerminalDepositPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LogisticsTerminalDepositPayload::pos,
            ItemResource.STREAM_CODEC, LogisticsTerminalDepositPayload::item,
            LogisticsTerminalDepositPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
