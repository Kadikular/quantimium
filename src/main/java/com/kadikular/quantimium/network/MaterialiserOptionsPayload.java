package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.unrealised.Materialising;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * What the open Materialiser could make of its Matter, which it's set to, and the Matter it's reading
 * (through a Tesseract, the first found and how many of that history): for its screen.
 */
public record MaterialiserOptionsPayload(List<Materialising.Option> options, Optional<Materialising.Target> target,
                                         ItemStack reading) implements CustomPacketPayload {

    public static final Type<MaterialiserOptionsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "materialiser_options"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialiserOptionsPayload> STREAM_CODEC = StreamCodec.composite(
            Materialising.Option.LIST_STREAM_CODEC, MaterialiserOptionsPayload::options,
            ByteBufCodecs.optional(Materialising.Target.STREAM_CODEC), MaterialiserOptionsPayload::target,
            ItemStack.OPTIONAL_STREAM_CODEC, MaterialiserOptionsPayload::reading,
            MaterialiserOptionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
