package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A click on the open Logistics Terminal's grid: take {@code count} of {@code item}, onto the cursor or, with
 * {@code toInventory}, into the inventory.
 */
public record LogisticsTerminalTakePayload(BlockPos pos, ItemResource item, int count, boolean toInventory) implements CustomPacketPayload {

    public static final Type<LogisticsTerminalTakePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "logistics_terminal_take"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTerminalTakePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LogisticsTerminalTakePayload::pos,
            ItemResource.STREAM_CODEC, LogisticsTerminalTakePayload::item,
            ByteBufCodecs.VAR_INT, LogisticsTerminalTakePayload::count,
            ByteBufCodecs.BOOL, LogisticsTerminalTakePayload::toInventory,
            LogisticsTerminalTakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
