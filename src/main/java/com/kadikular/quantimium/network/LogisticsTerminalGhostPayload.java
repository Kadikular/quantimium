package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Something dragged from an open Logistics Terminal's item grid onto crafting grid slot {@code slot}: made there when a craft is taken. */
public record LogisticsTerminalGhostPayload(BlockPos pos, int slot, ItemStack item) implements CustomPacketPayload {

    public static final Type<LogisticsTerminalGhostPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "logistics_terminal_ghost"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTerminalGhostPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LogisticsTerminalGhostPayload::pos,
            ByteBufCodecs.VAR_INT, LogisticsTerminalGhostPayload::slot,
            ItemStack.STREAM_CODEC, LogisticsTerminalGhostPayload::item,
            LogisticsTerminalGhostPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
