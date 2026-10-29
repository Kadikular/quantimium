package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sets the channel of what the open menu {@code containerId} is for (a {@link com.kadikular.quantimium.menu.ChannelMenu}). */
public record SetChannelPayload(int containerId, int channel) implements CustomPacketPayload {

    public static final Type<SetChannelPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "set_channel"));

    public static final StreamCodec<FriendlyByteBuf, SetChannelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetChannelPayload::containerId,
            ByteBufCodecs.VAR_INT, SetChannelPayload::channel,
            SetChannelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
