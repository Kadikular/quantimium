package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.unrealised.Materialising;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** The player picked an ore at a Materialiser, or cleared the choice. */
public record SetMaterialiserTargetPayload(BlockPos pos, Optional<Materialising.Target> target) implements CustomPacketPayload {

    public static final Type<SetMaterialiserTargetPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "set_materialiser_target"));

    public static final StreamCodec<FriendlyByteBuf, SetMaterialiserTargetPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetMaterialiserTargetPayload::pos,
            ByteBufCodecs.optional(Materialising.Target.STREAM_CODEC), SetMaterialiserTargetPayload::target,
            SetMaterialiserTargetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
