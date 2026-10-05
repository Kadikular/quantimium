// Path: src/main/java/com/kadikular/quantimium/block/entity/simulation/MachineSlotInfo.java
package com.kadikular.quantimium.block.entity.simulation;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client-facing description of a single slot on the contained machine, used to populate the slot
 * configuration pop-up.
 */
public record MachineSlotInfo(int slotIndex, @Nullable Direction face, ItemStack contents,
                              boolean acceptsInput, boolean producesOutput, int limit) {

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineSlotInfo> STREAM_CODEC = StreamCodec.of(
            (buf, info) -> {
                buf.writeVarInt(info.slotIndex);
                buf.writeByte(info.face == null ? -1 : info.face.get3DDataValue());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, info.contents);
                buf.writeBoolean(info.acceptsInput);
                buf.writeBoolean(info.producesOutput);
                buf.writeVarInt(info.limit);
            },
            buf -> {
                int slotIndex = buf.readVarInt();
                int faceId = buf.readByte();
                ItemStack contents = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                boolean acceptsInput = buf.readBoolean();
                boolean producesOutput = buf.readBoolean();
                int limit = buf.readVarInt();
                return new MachineSlotInfo(slotIndex, faceId < 0 ? null : Direction.from3DDataValue(faceId),
                        contents, acceptsInput, producesOutput, limit);
            });

    public String roleLabel() {
        if (acceptsInput && producesOutput) return "I/O";
        if (acceptsInput) return "IN";
        if (producesOutput) return "OUT";
        return "--";
    }

    public String faceLabel() {
        return face == null ? "INTERNAL" : face.getName().toUpperCase();
    }
}
