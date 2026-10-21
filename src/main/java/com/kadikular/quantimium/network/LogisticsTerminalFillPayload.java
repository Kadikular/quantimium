package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.menu.LogisticsTerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A recipe viewer's recipe put into the open Logistics Terminal's crafting grid: for each of the nine grid slots,
 * the items it may be filled with (none: left empty); with {@code max}, as many crafts' worth as there are.
 */
public record LogisticsTerminalFillPayload(BlockPos pos, List<List<ItemStack>> options, boolean max) implements CustomPacketPayload {

    public static final Type<LogisticsTerminalFillPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "logistics_terminal_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTerminalFillPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LogisticsTerminalFillPayload::pos,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(LogisticsTerminalMenu.MAX_OPTIONS)).apply(ByteBufCodecs.list(9)), LogisticsTerminalFillPayload::options,
            ByteBufCodecs.BOOL, LogisticsTerminalFillPayload::max,
            LogisticsTerminalFillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
