package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.unrealised.Materialising;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A click on a form at an open Materialiser: {@code count} of it made, onto the cursor or into the inventory. */
public record MaterialiserTakePayload(BlockPos pos, Materialising.Target form, int count, boolean toInventory) implements CustomPacketPayload {

    public static final Type<MaterialiserTakePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "materialiser_take"));

    public static final StreamCodec<FriendlyByteBuf, MaterialiserTakePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MaterialiserTakePayload::pos,
            Materialising.Target.STREAM_CODEC, MaterialiserTakePayload::form,
            ByteBufCodecs.VAR_INT, MaterialiserTakePayload::count,
            ByteBufCodecs.BOOL, MaterialiserTakePayload::toInventory,
            MaterialiserTakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
