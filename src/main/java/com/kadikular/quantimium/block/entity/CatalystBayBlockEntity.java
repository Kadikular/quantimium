package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Up to {@link #SLOTS} catalysts in a window of void, one in each quarter. The Horizon Core reads them
 * each time it looks over its reactor, so swapping a catalyst changes what the Reactor can make within
 * a second.
 */
public class CatalystBayBlockEntity extends BlockEntity {

    public static final int SLOTS = 4;

    private final ItemStack[] catalysts = new ItemStack[SLOTS];

    public CatalystBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CATALYST_BAY_BE.get(), pos, state);
        Arrays.fill(catalysts, ItemStack.EMPTY);
    }

    /**
     * The slot under a point on the top face, in block-local x and z: north-west, north-east,
     * south-west, south-east.
     */
    public static int slotAt(double x, double z) {
        return (x >= 0.5 ? 1 : 0) + (z >= 0.5 ? 2 : 0);
    }

    /** The centre of a slot's quarter, in block-local x and z. */
    public static double slotX(int slot) {
        return (slot & 1) == 0 ? 0.34 : 0.66;
    }

    public static double slotZ(int slot) {
        return (slot & 2) == 0 ? 0.34 : 0.66;
    }

    public ItemStack getCatalyst(int slot) {
        return catalysts[slot];
    }

    /** Every slot, empty ones too, so a catalyst keeps its index while the others change. */
    public List<ItemStack> getCatalysts() {
        return List.of(catalysts);
    }

    public boolean isEmpty() {
        for (ItemStack catalyst : catalysts) if (!catalyst.isEmpty()) return false;
        return true;
    }

    /** Where a new catalyst goes: the slot clicked if it's free, else the first free one, else -1. */
    public int slotToFill(int clicked) {
        if (clicked >= 0 && catalysts[clicked].isEmpty()) return clicked;
        for (int i = 0; i < SLOTS; i++) if (catalysts[i].isEmpty()) return i;
        return -1;
    }

    /** Which catalyst an empty hand takes: the one clicked, else the last one in, else -1. */
    public int slotToEmpty(int clicked) {
        if (clicked >= 0 && !catalysts[clicked].isEmpty()) return clicked;
        for (int i = SLOTS - 1; i >= 0; i--) if (!catalysts[i].isEmpty()) return i;
        return -1;
    }

    public void setCatalyst(int slot, ItemStack stack) {
        catalysts[slot] = stack.copyWithCount(Math.min(1, stack.getCount()));
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        for (int i = 0; i < SLOTS; i++) {
            if (level != null && !catalysts[i].isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), catalysts[i]);
            }
            catalysts[i] = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Catalysts", ItemStack.OPTIONAL_CODEC.listOf(), List.of(catalysts));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        Arrays.fill(catalysts, ItemStack.EMPTY);
        List<ItemStack> read = new ArrayList<>(in.read("Catalysts", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        // A raised bay, from before bays sank into the floor, held one.
        if (read.isEmpty()) in.read("Catalyst", ItemStack.OPTIONAL_CODEC).ifPresent(read::add);
        for (int i = 0; i < Math.min(SLOTS, read.size()); i++) catalysts[i] = read.get(i);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
