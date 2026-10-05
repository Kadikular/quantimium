package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The stash key was pressed: open the player's stash, if a Stash module holds one of their doubles. */
public record OpenStashPayload() implements CustomPacketPayload {

    public static final OpenStashPayload INSTANCE = new OpenStashPayload();

    public static final Type<OpenStashPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "open_stash"));

    public static final StreamCodec<ByteBuf, OpenStashPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
