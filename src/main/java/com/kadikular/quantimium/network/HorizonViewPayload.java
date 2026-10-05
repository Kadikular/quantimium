package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;
import java.util.Optional;

/**
 * What the open Horizon Core's screen shows: its load, everything it holds or could make with how
 * many of each when that has changed since the last one (it can run to thousands of entries), and the
 * last request's result.
 */
public record HorizonViewPayload(BlockPos pos, long mass, long capacity, int rings, boolean active,
                                 Optional<List<Entry>> entries, Optional<Component> message)
        implements CustomPacketPayload {

    /** One item: how many are held, and how many could be had in all, held or made. */
    public record Entry(ItemResource item, long held, long total) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Entry::item,
                ByteBufCodecs.VAR_LONG, Entry::held,
                ByteBufCodecs.VAR_LONG, Entry::total,
                Entry::new);
    }

    public static final Type<HorizonViewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "horizon_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HorizonViewPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HorizonViewPayload::pos,
            ByteBufCodecs.VAR_LONG, HorizonViewPayload::mass,
            ByteBufCodecs.VAR_LONG, HorizonViewPayload::capacity,
            ByteBufCodecs.VAR_INT, HorizonViewPayload::rings,
            ByteBufCodecs.BOOL, HorizonViewPayload::active,
            ByteBufCodecs.optional(Entry.STREAM_CODEC.apply(ByteBufCodecs.list())), HorizonViewPayload::entries,
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), HorizonViewPayload::message,
            HorizonViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
