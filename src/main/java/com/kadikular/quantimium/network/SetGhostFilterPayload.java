package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** A recipe viewer dropped {@code stack} on ghost filter slot {@code index} of the open menu. */
public record SetGhostFilterPayload(int containerId, int index, ItemStack stack) implements CustomPacketPayload {

    public static final Type<SetGhostFilterPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "set_ghost_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetGhostFilterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetGhostFilterPayload::containerId,
            ByteBufCodecs.VAR_INT, SetGhostFilterPayload::index,
            ItemStack.OPTIONAL_STREAM_CODEC, SetGhostFilterPayload::stack,
            SetGhostFilterPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
