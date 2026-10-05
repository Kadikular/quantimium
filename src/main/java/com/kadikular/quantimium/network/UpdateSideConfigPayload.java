package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record UpdateSideConfigPayload(BlockPos pos, List<SideConfig> configs) implements CustomPacketPayload {
    public static final Type<UpdateSideConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "update_side_config"));
    public static final StreamCodec<FriendlyByteBuf, UpdateSideConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                List<SideConfig> configs = SideConfig.normalize(payload.configs);
                buf.writeVarInt(configs.size());
                for (SideConfig config : configs) SideConfig.STREAM_CODEC.encode(buf, config);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int count = Math.min(6, buf.readVarInt());
                List<SideConfig> configs = new ArrayList<>(count);
                for (int i = 0; i < count; i++) configs.add(SideConfig.STREAM_CODEC.decode(buf));
                return new UpdateSideConfigPayload(pos, configs);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
