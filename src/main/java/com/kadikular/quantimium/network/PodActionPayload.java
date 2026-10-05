package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Something done on a pod's screen: swap into a double, the Anchor, the listing, or folding its double. */
public record PodActionPayload(BlockPos pos, int action, UUID target) implements CustomPacketPayload {

    public static final int SWAP = 0;
    public static final int TOGGLE_ANCHOR = 1;
    public static final int TOGGLE_LISTED = 2;
    public static final int FOLD = 3;
    /** Send a spare double to an empty pod through the hub's Relay, then swap into it. */
    public static final int RELAY = 4;
    /** Bring a field double, or a knocked-out Sophon, home into this pod with its Recovery module. */
    public static final int RECOVER = 5;
    /** Swap from the Tether in hand; the position is ignored. */
    public static final int TETHER_SWAP = 6;

    public static final Type<PodActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "pod_action"));

    public static final StreamCodec<FriendlyByteBuf, PodActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PodActionPayload::pos,
            ByteBufCodecs.VAR_INT, PodActionPayload::action,
            UUIDUtil.STREAM_CODEC, PodActionPayload::target,
            PodActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
