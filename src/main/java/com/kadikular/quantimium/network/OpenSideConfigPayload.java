package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record OpenSideConfigPayload(
        BlockPos pos,
        List<SideConfig> configs,
        SideAutomationProfile profile) implements CustomPacketPayload {
    public static final Type<OpenSideConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "open_side_config"));
    public static final StreamCodec<FriendlyByteBuf, OpenSideConfigPayload> STREAM_CODEC = codec();

    private static StreamCodec<FriendlyByteBuf, OpenSideConfigPayload> codec() {
        return StreamCodec.of((buf, payload) -> {
            buf.writeBlockPos(payload.pos);
            List<SideConfig> configs = SideConfig.normalize(payload.configs);
            buf.writeVarInt(configs.size());
            for (SideConfig config : configs) SideConfig.STREAM_CODEC.encode(buf, config);
            SideAutomationProfile.STREAM_CODEC.encode(buf, payload.profile);
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            int count = Math.min(6, buf.readVarInt());
            List<SideConfig> configs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) configs.add(SideConfig.STREAM_CODEC.decode(buf));
            SideAutomationProfile profile = SideAutomationProfile.STREAM_CODEC.decode(buf);
            return new OpenSideConfigPayload(pos, SideConfig.normalize(configs), profile);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
