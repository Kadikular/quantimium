package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** The player asked the open Horizon Core for {@code count} of {@code item}, to its Output ports. */
public record HorizonRequestPayload(BlockPos pos, ItemResource item, int count) implements CustomPacketPayload {

    public static final Type<HorizonRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "horizon_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HorizonRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HorizonRequestPayload::pos,
            ItemResource.STREAM_CODEC, HorizonRequestPayload::item,
            ByteBufCodecs.VAR_INT, HorizonRequestPayload::count,
            HorizonRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
