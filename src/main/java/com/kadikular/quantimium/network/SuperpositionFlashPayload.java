package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Tells a player they have just changed bodies, so their screen can show it: a swap or a rescue. */
public record SuperpositionFlashPayload(int kind) implements CustomPacketPayload {

    public static final int SWAP = 0;
    public static final int RESCUE = 1;

    public static final Type<SuperpositionFlashPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "superposition_flash"));

    public static final StreamCodec<FriendlyByteBuf, SuperpositionFlashPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SuperpositionFlashPayload::kind,
            SuperpositionFlashPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
