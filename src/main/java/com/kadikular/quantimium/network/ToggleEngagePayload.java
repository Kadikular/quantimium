// Path: src/main/java/com/kadikular/quantimium/network/ToggleEngagePayload.java
package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ToggleEngagePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ToggleEngagePayload> TYPE = 
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "toggle_engage"));

    public static final StreamCodec<FriendlyByteBuf, ToggleEngagePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ToggleEngagePayload::pos,
            ToggleEngagePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}