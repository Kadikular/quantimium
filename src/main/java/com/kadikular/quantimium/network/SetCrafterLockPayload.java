package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetCrafterLockPayload(BlockPos pos, boolean locked) implements CustomPacketPayload {

    public static final Type<SetCrafterLockPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "set_crafter_lock"));

    public static final StreamCodec<ByteBuf, SetCrafterLockPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetCrafterLockPayload::pos,
            ByteBufCodecs.BOOL, SetCrafterLockPayload::locked,
            SetCrafterLockPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
