package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A pod renamed from its screen; a blank name puts the default back. */
public record RenamePodPayload(BlockPos pos, String name) implements CustomPacketPayload {

    public static final Type<RenamePodPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "rename_pod"));

    public static final StreamCodec<FriendlyByteBuf, RenamePodPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RenamePodPayload::pos,
            ByteBufCodecs.stringUtf8(64), RenamePodPayload::name,
            RenamePodPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
