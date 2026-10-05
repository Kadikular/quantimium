package com.kadikular.quantimium.block.entity.simulation;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-face automation for items and fluids.
 *
 * <p>Items and fluids each have their own mode, active-slot masks, and directional auto-transfer
 * flags so a face can pull water without automatically draining products back into the same tank.
 */
public record SideConfig(
        Direction side,
        SideMode itemMode,
        int itemInputMask,
        int itemOutputMask,
        boolean autoItemInput,
        boolean autoItemOutput,
        SideMode fluidMode,
        int fluidInputMask,
        int fluidOutputMask,
        boolean autoFluidInput,
        boolean autoFluidOutput) {

    public static final int ALL_ITEM_INPUTS = 0x1FF;
    public static final int ALL_ITEM_OUTPUTS = 0x1FF;
    private static final int CRAFTER_OUTPUT_SLOTS = 54;
    public static final int ALL_FLUID_INPUTS = SimulatorFluidTanks.ALL_INPUTS;
    public static final int ALL_FLUID_OUTPUTS = SimulatorFluidTanks.ALL_OUTPUTS;

    /** @deprecated use {@link #ALL_ITEM_INPUTS} */
    @Deprecated
    public static final int ALL_INPUTS = ALL_ITEM_INPUTS;

    public static final StreamCodec<FriendlyByteBuf, SideConfig> STREAM_CODEC = StreamCodec.of(
            (buf, config) -> {
                buf.writeByte(config.side.get3DDataValue());
                buf.writeByte(config.itemMode.ordinal());
                buf.writeVarInt(config.itemInputMask);
                buf.writeVarInt(config.itemOutputMask);
                buf.writeBoolean(config.autoItemInput);
                buf.writeBoolean(config.autoItemOutput);
                buf.writeByte(config.fluidMode.ordinal());
                buf.writeVarInt(config.fluidInputMask);
                buf.writeVarInt(config.fluidOutputMask);
                buf.writeBoolean(config.autoFluidInput);
                buf.writeBoolean(config.autoFluidOutput);
            },
            buf -> new SideConfig(
                    Direction.from3DDataValue(buf.readByte()),
                    SideMode.byId(buf.readByte()),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    SideMode.byId(buf.readByte()),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean()));

    public SideConfig {
        itemInputMask &= ALL_ITEM_INPUTS;
        itemOutputMask &= ALL_ITEM_OUTPUTS;
        fluidInputMask &= ALL_FLUID_INPUTS;
        fluidOutputMask &= ALL_FLUID_OUTPUTS;
    }

    public boolean allowsInputSlot(int slot) {
        return itemMode.allowsInput() && slot >= 0 && slot < 9 && (itemInputMask & (1 << slot)) != 0;
    }

    public boolean allowsOutputSlot(int slot) {
        // Crafters repeat the nine-position mask across six catalog pages. Simulators only use
        // the first page, so this remains backwards compatible for both machines.
        int local = slot - 9;
        return itemMode.allowsOutput() && local >= 0 && local < CRAFTER_OUTPUT_SLOTS
                && (itemOutputMask & (1 << (local % 9))) != 0;
    }

    public boolean allowsFluidInputTank(int tank) {
        return fluidMode.allowsInput() && tank >= 0 && tank < SimulatorFluidTanks.INPUT_TANKS
                && (fluidInputMask & (1 << tank)) != 0;
    }

    public boolean allowsFluidOutputTank(int tank) {
        int local = tank - SimulatorFluidTanks.INPUT_TANKS;
        return fluidMode.allowsOutput() && local >= 0 && local < SimulatorFluidTanks.OUTPUT_TANKS
                && (fluidOutputMask & (1 << local)) != 0;
    }

    /** Disables unsupported media without disturbing the saved masks for media the machine supports. */
    public SideConfig withMediaEnabled(boolean itemsEnabled, boolean fluidsEnabled) {
        return new SideConfig(side,
                itemsEnabled ? itemMode : SideMode.DISABLED,
                itemInputMask, itemOutputMask,
                itemsEnabled && autoItemInput, itemsEnabled && autoItemOutput,
                fluidsEnabled ? fluidMode : SideMode.DISABLED,
                fluidInputMask, fluidOutputMask,
                fluidsEnabled && autoFluidInput, fluidsEnabled && autoFluidOutput);
    }

    public SideConfig withItemMode(SideMode mode) {
        return new SideConfig(side, mode, itemInputMask, itemOutputMask, autoItemInput, autoItemOutput,
                fluidMode, fluidInputMask, fluidOutputMask, autoFluidInput, autoFluidOutput);
    }

    public SideConfig withFluidMode(SideMode mode) {
        return new SideConfig(side, itemMode, itemInputMask, itemOutputMask, autoItemInput, autoItemOutput,
                mode, fluidInputMask, fluidOutputMask, autoFluidInput, autoFluidOutput);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("Side", (byte) side.get3DDataValue());
        tag.putByte("ItemMode", (byte) itemMode.ordinal());
        tag.putInt("ItemInputMask", itemInputMask);
        tag.putInt("ItemOutputMask", itemOutputMask);
        tag.putBoolean("AutoItemInput", autoItemInput);
        tag.putBoolean("AutoItemOutput", autoItemOutput);
        tag.putByte("FluidMode", (byte) fluidMode.ordinal());
        tag.putInt("FluidInputMask", fluidInputMask);
        tag.putInt("FluidOutputMask", fluidOutputMask);
        tag.putBoolean("AutoFluidInput", autoFluidInput);
        tag.putBoolean("AutoFluidOutput", autoFluidOutput);
        // Legacy keys so older tools reading Mode/Auto still see something sensible.
        tag.putByte("Mode", (byte) itemMode.ordinal());
        tag.putInt("InputMask", itemInputMask);
        tag.putBoolean("AutoItems", autoItemInput || autoItemOutput);
        tag.putBoolean("AutoFluids", autoFluidInput || autoFluidOutput);
        tag.putBoolean("Auto", autoItemInput || autoItemOutput);
        return tag;
    }

    public static SideConfig load(CompoundTag tag) {
        Direction side = Direction.from3DDataValue(tag.getByteOr("Side", (byte) 0));
        SideMode itemMode = SideMode.byId(tag.contains("ItemMode") ? tag.getByteOr("ItemMode", (byte) 0) : tag.getByteOr("Mode", (byte) 0));
        int itemInput = tag.contains("ItemInputMask") ? tag.getIntOr("ItemInputMask", 0)
                : tag.contains("InputMask") ? tag.getIntOr("InputMask", 0) : ALL_ITEM_INPUTS;
        int itemOutput = tag.contains("ItemOutputMask") ? tag.getIntOr("ItemOutputMask", 0) : ALL_ITEM_OUTPUTS;
        boolean legacyAutoItems = tag.contains("AutoItems")
                ? tag.getBooleanOr("AutoItems", false)
                : tag.getBooleanOr("Auto", false);
        boolean autoItemInput = tag.contains("AutoItemInput")
                ? tag.getBooleanOr("AutoItemInput", false)
                : legacyAutoItems;
        boolean autoItemOutput = tag.contains("AutoItemOutput")
                ? tag.getBooleanOr("AutoItemOutput", false)
                : legacyAutoItems;

        SideMode fluidMode = tag.contains("FluidMode")
                ? SideMode.byId(tag.getByteOr("FluidMode", (byte) 0))
                : SideMode.DISABLED;
        int fluidInput = tag.contains("FluidInputMask") ? tag.getIntOr("FluidInputMask", 0) : ALL_FLUID_INPUTS;
        int fluidOutput = tag.contains("FluidOutputMask") ? tag.getIntOr("FluidOutputMask", 0) : ALL_FLUID_OUTPUTS;
        boolean legacyAutoFluids = tag.contains("AutoFluids") && tag.getBooleanOr("AutoFluids", false);
        boolean autoFluidInput = tag.contains("AutoFluidInput")
                ? tag.getBooleanOr("AutoFluidInput", false)
                : legacyAutoFluids;
        boolean autoFluidOutput = tag.contains("AutoFluidOutput")
                ? tag.getBooleanOr("AutoFluidOutput", false)
                : legacyAutoFluids;

        return new SideConfig(side, itemMode, itemInput, itemOutput, autoItemInput, autoItemOutput,
                fluidMode, fluidInput, fluidOutput, autoFluidInput, autoFluidOutput);
    }

    public static List<SideConfig> defaults() {
        List<SideConfig> result = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            result.add(new SideConfig(side, SideMode.BOTH, ALL_ITEM_INPUTS, ALL_ITEM_OUTPUTS,
                    false, false, SideMode.DISABLED, ALL_FLUID_INPUTS, ALL_FLUID_OUTPUTS,
                    false, false));
        }
        return result;
    }

    public static List<SideConfig> normalize(List<SideConfig> incoming) {
        List<SideConfig> result = defaults();
        for (SideConfig config : incoming) {
            result.set(config.side.get3DDataValue(), config);
        }
        return result;
    }

    public static ListTag saveAll(List<SideConfig> configs) {
        ListTag list = new ListTag();
        for (SideConfig config : normalize(configs)) list.add(config.save());
        return list;
    }

    public static List<SideConfig> loadAll(ListTag list) {
        List<SideConfig> result = defaults();
        for (int i = 0; i < list.size(); i++) {
            SideConfig config = load(list.getCompoundOrEmpty(i));
            result.set(config.side.get3DDataValue(), config);
        }
        return result;
    }
}
