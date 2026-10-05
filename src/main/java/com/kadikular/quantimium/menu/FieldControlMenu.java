package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.FieldControlBlock;
import com.kadikular.quantimium.block.entity.FieldControlBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.kadikular.quantimium.util.SlotItemHandler;

/**
 * For all three field control blocks. The Siphon and Regulator have a take-only fragment slot at the
 * top right; the Maintainer has none. Layout: tools/gui_panels.py field_control(_fragments).
 */
public class FieldControlMenu extends AbstractContainerMenu {

    public static final int FRAGMENT_X = 152;
    public static final int FRAGMENT_Y = 20;

    private final FieldControlBlockEntity blockEntity;
    private final ContainerData data;
    private final int machineSlots;

    public FieldControlMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (FieldControlBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(FieldControlBlockEntity.DATA_COUNT));
    }

    public FieldControlMenu(int containerId, Inventory playerInv, FieldControlBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.FIELD_CONTROL_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, FieldControlBlockEntity.DATA_COUNT);
        if (blockEntity.kind().pullsAnomaly()) {
            addSlot(new SlotItemHandler(blockEntity.getInventory(), 0, FRAGMENT_X, FRAGMENT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        machineSlots = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    public FieldControlBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public FieldControlBlock.Kind kind() {
        return blockEntity.kind();
    }

    public int getEnergyStored() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int getMaxEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getStatus() {
        return data.get(4);
    }

    public FluxBand getFloor() {
        return FieldControlBlockEntity.FLOORS[Math.floorMod(data.get(5), FieldControlBlockEntity.FLOORS.length)];
    }

    public FieldControlBlockEntity.Ceiling getCeiling() {
        return FieldControlBlockEntity.CEILINGS[Math.floorMod(data.get(6), FieldControlBlockEntity.CEILINGS.length)];
    }

    public int getFlux() {
        return (data.get(8) << 16) | (data.get(7) & 0xFFFF);
    }

    public int getAnomaly() {
        return (data.get(10) << 16) | (data.get(9) & 0xFFFF);
    }

    public int getAverageFe() {
        return (data.get(12) << 16) | (data.get(11) & 0xFFFF);
    }

    /** Towards the next fragment, 0 to 1. */
    public float getFragmentProgress() {
        return data.get(13) / 1000.0f;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        blockEntity.pressButton(id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= machineSlots) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, blockEntity.getBlockState().getBlock());
    }
}
