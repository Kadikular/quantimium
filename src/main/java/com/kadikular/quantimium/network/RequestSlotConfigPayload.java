// Path: src/main/java/com/kadikular/quantimium/network/RequestSlotConfigPayload.java
package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Asks the server for the contained machine's slot layout so the configuration pop-up can be filled
 * in. Set {@code autoDetect} to have the server redo its detection pass first.
 */
public record RequestSlotConfigPayload(BlockPos pos, boolean autoDetect) implements CustomPacketPayload {

    public static final Type<RequestSlotConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "request_slot_config"));

    public static final StreamCodec<FriendlyByteBuf, RequestSlotConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestSlotConfigPayload::pos,
            ByteBufCodecs.BOOL, RequestSlotConfigPayload::autoDetect,
            RequestSlotConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
