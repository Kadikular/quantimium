package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A click on an open Materialiser's grid with something it makes on the cursor: all of it (or one) back into its buffer. */
public record MaterialiserPutPayload(BlockPos pos, boolean one) implements CustomPacketPayload {

    public static final Type<MaterialiserPutPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "materialiser_put"));

    public static final StreamCodec<FriendlyByteBuf, MaterialiserPutPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MaterialiserPutPayload::pos,
            ByteBufCodecs.BOOL, MaterialiserPutPayload::one,
            MaterialiserPutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
