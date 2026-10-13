package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;

/** What the open Logistics Terminal shows: whether it's on a network, and what that network can give, with how many of each. */
public record LogisticsTerminalViewPayload(BlockPos pos, boolean connected, List<Entry> entries) implements CustomPacketPayload {

    /** One thing: how many the network has, and how many more a Reactor on it could make. */
    public record Entry(ItemResource item, long count, long craftable) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Entry::item,
                ByteBufCodecs.VAR_LONG, Entry::count,
                ByteBufCodecs.VAR_LONG, Entry::craftable,
                Entry::new);
    }

    public static final Type<LogisticsTerminalViewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "logistics_terminal_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTerminalViewPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LogisticsTerminalViewPayload::pos,
            ByteBufCodecs.BOOL, LogisticsTerminalViewPayload::connected,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), LogisticsTerminalViewPayload::entries,
            LogisticsTerminalViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
