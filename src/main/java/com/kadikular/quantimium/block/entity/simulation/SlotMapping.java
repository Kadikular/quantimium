// Path: src/main/java/com/kadikular/quantimium/block/entity/simulation/SlotMapping.java
package com.kadikular.quantimium.block.entity.simulation;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds one slot of the simulator's 3x3 input grid to a slot of the contained machine.
 *
 * <p>{@code targetSlotIndex} of {@link #AUTO} lets the phantom mirror route the item to any machine
 * slot that will accept it, which is the default until the player pins a slot down by hand.
 */
public record SlotMapping(int simulatorSlot, int targetSlotIndex, @Nullable Direction targetFace, boolean locked) {

    public static final int AUTO = -1;

    public static final StreamCodec<FriendlyByteBuf, SlotMapping> STREAM_CODEC = StreamCodec.of(
            (buf, mapping) -> {
                buf.writeVarInt(mapping.simulatorSlot);
                buf.writeVarInt(mapping.targetSlotIndex + 1);
                buf.writeByte(mapping.targetFace == null ? -1 : mapping.targetFace.get3DDataValue());
                buf.writeBoolean(mapping.locked);
            },
            buf -> {
                int simulatorSlot = buf.readVarInt();
                int targetSlotIndex = buf.readVarInt() - 1;
                int faceId = buf.readByte();
                boolean locked = buf.readBoolean();
                return new SlotMapping(simulatorSlot, targetSlotIndex,
                        faceId < 0 ? null : Direction.from3DDataValue(faceId), locked);
            });

    public static SlotMapping auto(int simulatorSlot) {
        return new SlotMapping(simulatorSlot, AUTO, null, false);
    }

    public static SlotMapping locked(int simulatorSlot) {
        return new SlotMapping(simulatorSlot, AUTO, null, true);
    }

    public static SlotMapping bound(int simulatorSlot, int targetSlotIndex, @Nullable Direction targetFace) {
        return new SlotMapping(simulatorSlot, targetSlotIndex, targetFace, false);
    }

    public boolean isBound() {
        return !locked && targetSlotIndex >= 0;
    }

    public boolean isAuto() {
        return !locked && targetSlotIndex < 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Slot", simulatorSlot);
        tag.putInt("Target", targetSlotIndex);
        tag.putByte("Face", (byte) (targetFace == null ? -1 : targetFace.get3DDataValue()));
        tag.putBoolean("Locked", locked);
        return tag;
    }

    public static SlotMapping load(CompoundTag tag) {
        byte faceId = tag.getByteOr("Face", (byte) 0);
        return new SlotMapping(
                tag.getIntOr("Slot", 0),
                tag.contains("Target") ? tag.getIntOr("Target", 0) : AUTO,
                faceId < 0 ? null : Direction.from3DDataValue(faceId),
                tag.getBooleanOr("Locked", false));
    }

    /** All grid slots unlocked and free to route anywhere. */
    public static List<SlotMapping> defaults(int slotCount) {
        List<SlotMapping> mappings = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            mappings.add(auto(slot));
        }
        return mappings;
    }

    public static ListTag saveAll(List<SlotMapping> mappings) {
        ListTag list = new ListTag();
        for (SlotMapping mapping : mappings) {
            list.add(mapping.save());
        }
        return list;
    }

    /**
     * Reads a mapping list, always returning exactly {@code slotCount} entries so callers can index
     * by grid slot without bounds checks even if the saved data is stale or truncated.
     */
    public static List<SlotMapping> loadAll(ListTag list, int slotCount) {
        List<SlotMapping> mappings = defaults(slotCount);
        for (int i = 0; i < list.size(); i++) {
            SlotMapping mapping = load(list.getCompoundOrEmpty(i));
            if (mapping.simulatorSlot() >= 0 && mapping.simulatorSlot() < slotCount) {
                mappings.set(mapping.simulatorSlot(), mapping);
            }
        }
        return mappings;
    }

    public static List<SlotMapping> normalize(List<SlotMapping> incoming, int slotCount) {
        List<SlotMapping> mappings = defaults(slotCount);
        for (SlotMapping mapping : incoming) {
            if (mapping.simulatorSlot() >= 0 && mapping.simulatorSlot() < slotCount) {
                mappings.set(mapping.simulatorSlot(), mapping);
            }
        }
        return mappings;
    }

    public static int lockedMask(List<SlotMapping> mappings) {
        int mask = 0;
        for (SlotMapping mapping : mappings) {
            if (mapping.locked() && mapping.simulatorSlot() >= 0 && mapping.simulatorSlot() < 32) {
                mask |= 1 << mapping.simulatorSlot();
            }
        }
        return mask;
    }
}
