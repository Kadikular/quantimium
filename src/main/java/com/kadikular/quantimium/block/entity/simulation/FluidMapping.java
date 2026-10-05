package com.kadikular.quantimium.block.entity.simulation;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Binds one simulator input tank to a tank on the contained machine. */
public record FluidMapping(int simulatorTank, int targetTankIndex, @Nullable Direction targetFace, boolean locked) {

    public static final int AUTO = -1;

    public static final StreamCodec<FriendlyByteBuf, FluidMapping> STREAM_CODEC = StreamCodec.of(
            (buf, mapping) -> {
                buf.writeVarInt(mapping.simulatorTank);
                buf.writeVarInt(mapping.targetTankIndex + 1);
                buf.writeByte(mapping.targetFace == null ? -1 : mapping.targetFace.get3DDataValue());
                buf.writeBoolean(mapping.locked);
            },
            buf -> {
                int simulatorTank = buf.readVarInt();
                int targetTankIndex = buf.readVarInt() - 1;
                int faceId = buf.readByte();
                boolean locked = buf.readBoolean();
                return new FluidMapping(simulatorTank, targetTankIndex,
                        faceId < 0 ? null : Direction.from3DDataValue(faceId), locked);
            });

    public static FluidMapping auto(int simulatorTank) {
        return new FluidMapping(simulatorTank, AUTO, null, false);
    }

    public static FluidMapping locked(int simulatorTank) {
        return new FluidMapping(simulatorTank, AUTO, null, true);
    }

    public static FluidMapping bound(int simulatorTank, int targetTankIndex, @Nullable Direction targetFace) {
        return new FluidMapping(simulatorTank, targetTankIndex, targetFace, false);
    }

    public boolean isBound() {
        return !locked && targetTankIndex >= 0;
    }

    public boolean isAuto() {
        return !locked && targetTankIndex < 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Tank", simulatorTank);
        tag.putInt("Target", targetTankIndex);
        tag.putByte("Face", (byte) (targetFace == null ? -1 : targetFace.get3DDataValue()));
        tag.putBoolean("Locked", locked);
        return tag;
    }

    public static FluidMapping load(CompoundTag tag) {
        byte faceId = tag.getByteOr("Face", (byte) 0);
        return new FluidMapping(
                tag.getIntOr("Tank", 0),
                tag.contains("Target") ? tag.getIntOr("Target", 0) : AUTO,
                faceId < 0 ? null : Direction.from3DDataValue(faceId),
                tag.getBooleanOr("Locked", false));
    }

    public static List<FluidMapping> defaults(int tankCount) {
        List<FluidMapping> mappings = new ArrayList<>(tankCount);
        for (int tank = 0; tank < tankCount; tank++) {
            mappings.add(auto(tank));
        }
        return mappings;
    }

    public static ListTag saveAll(List<FluidMapping> mappings) {
        ListTag list = new ListTag();
        for (FluidMapping mapping : mappings) list.add(mapping.save());
        return list;
    }

    public static List<FluidMapping> loadAll(ListTag list, int tankCount) {
        List<FluidMapping> mappings = defaults(tankCount);
        for (int i = 0; i < list.size(); i++) {
            FluidMapping mapping = load(list.getCompoundOrEmpty(i));
            if (mapping.simulatorTank() >= 0 && mapping.simulatorTank() < tankCount) {
                mappings.set(mapping.simulatorTank(), mapping);
            }
        }
        return mappings;
    }

    public static List<FluidMapping> normalize(List<FluidMapping> incoming, int tankCount) {
        List<FluidMapping> mappings = defaults(tankCount);
        for (FluidMapping mapping : incoming) {
            if (mapping.simulatorTank() >= 0 && mapping.simulatorTank() < tankCount) {
                mappings.set(mapping.simulatorTank(), mapping);
            }
        }
        return mappings;
    }
}
