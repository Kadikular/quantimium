package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.simulation.FluidMapping;
import com.kadikular.quantimium.block.entity.simulation.MachineSlotInfo;
import com.kadikular.quantimium.block.entity.simulation.MachineTankInfo;
import com.kadikular.quantimium.block.entity.simulation.SlotMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Server response carrying the contained machine's item/fluid layout and current bindings. */
public record OpenSlotConfigPayload(BlockPos pos, List<MachineSlotInfo> machineSlots,
                                    List<SlotMapping> mappings,
                                    List<MachineTankInfo> machineTanks,
                                    List<FluidMapping> fluidMappings) implements CustomPacketPayload {

    private static final int MAX_ENTRIES = 512;

    public static final Type<OpenSlotConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "open_slot_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSlotConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                writeRegistry(buf, payload.machineSlots, MachineSlotInfo.STREAM_CODEC);
                writeFriendly(buf, payload.mappings, SlotMapping.STREAM_CODEC);
                writeRegistry(buf, payload.machineTanks, MachineTankInfo.STREAM_CODEC);
                writeFriendly(buf, payload.fluidMappings, FluidMapping.STREAM_CODEC);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                List<MachineSlotInfo> machineSlots = readRegistry(buf, MachineSlotInfo.STREAM_CODEC);
                List<SlotMapping> mappings = readFriendly(buf, SlotMapping.STREAM_CODEC);
                List<MachineTankInfo> machineTanks = readRegistry(buf, MachineTankInfo.STREAM_CODEC);
                List<FluidMapping> fluidMappings = readFriendly(buf, FluidMapping.STREAM_CODEC);
                return new OpenSlotConfigPayload(pos, machineSlots, mappings, machineTanks, fluidMappings);
            });

    private static <T> void writeRegistry(RegistryFriendlyByteBuf buf, List<T> list,
                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        int count = Math.min(list.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) codec.encode(buf, list.get(i));
    }

    private static <T> List<T> readRegistry(RegistryFriendlyByteBuf buf,
                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<T> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(codec.decode(buf));
        return list;
    }

    private static <T> void writeFriendly(FriendlyByteBuf buf, List<T> list,
                                          StreamCodec<? super FriendlyByteBuf, T> codec) {
        int count = Math.min(list.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) codec.encode(buf, list.get(i));
    }

    private static <T> List<T> readFriendly(FriendlyByteBuf buf,
                                            StreamCodec<? super FriendlyByteBuf, T> codec) {
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
