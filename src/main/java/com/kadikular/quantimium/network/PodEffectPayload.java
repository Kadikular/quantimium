package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A superposition effect for everyone who can see it: a pod someone left or arrived in, a double
 * unfolded or folded, or the echo of a body that died and was let go. See
 * {@link com.kadikular.quantimium.superposition.Superposition}'s {@code EFFECT_} kinds.
 */
public record PodEffectPayload(BlockPos pos, int kind) implements CustomPacketPayload {

    public static final Type<PodEffectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "pod_effect"));

    public static final StreamCodec<FriendlyByteBuf, PodEffectPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PodEffectPayload::pos,
            ByteBufCodecs.VAR_INT, PodEffectPayload::kind,
            PodEffectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
