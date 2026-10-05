package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.simulation.FluidMapping;
import com.kadikular.quantimium.block.entity.simulation.SlotMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Sends hand-edited item and fluid bindings back to the simulator. */
public record UpdateSlotMappingPayload(BlockPos pos, List<SlotMapping> mappings,
                                       List<FluidMapping> fluidMappings) implements CustomPacketPayload {

    private static final int MAX_ENTRIES = 64;

    public static final Type<UpdateSlotMappingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "update_slot_mapping"));

    public static final StreamCodec<FriendlyByteBuf, UpdateSlotMappingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                write(buf, payload.mappings, SlotMapping.STREAM_CODEC);
                write(buf, payload.fluidMappings, FluidMapping.STREAM_CODEC);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                List<SlotMapping> mappings = read(buf, SlotMapping.STREAM_CODEC);
                List<FluidMapping> fluidMappings = read(buf, FluidMapping.STREAM_CODEC);
                return new UpdateSlotMappingPayload(pos, mappings, fluidMappings);
            });

    private static <T> void write(FriendlyByteBuf buf, List<T> list, StreamCodec<? super FriendlyByteBuf, T> codec) {
        int count = Math.min(list.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) codec.encode(buf, list.get(i));
    }

    private static <T> List<T> read(FriendlyByteBuf buf, StreamCodec<? super FriendlyByteBuf, T> codec) {
        int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<T> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(codec.decode(buf));
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
