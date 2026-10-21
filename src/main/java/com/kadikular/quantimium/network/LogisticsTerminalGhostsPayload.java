package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** What each of an open Logistics Terminal's crafting grid slots will have made for it when a craft is taken (EMPTY: nothing). */
public record LogisticsTerminalGhostsPayload(int containerId, List<ItemStack> ghosts) implements CustomPacketPayload {

    public static final Type<LogisticsTerminalGhostsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "logistics_terminal_ghosts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTerminalGhostsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LogisticsTerminalGhostsPayload::containerId,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9)), LogisticsTerminalGhostsPayload::ghosts,
            LogisticsTerminalGhostsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
