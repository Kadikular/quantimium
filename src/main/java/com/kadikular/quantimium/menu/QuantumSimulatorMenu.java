// Path: src/main/java/com/kadikular/quantimium/menu/QuantumSimulatorMenu.java
package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModBlocks;
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
import net.minecraft.world.level.Level;
import com.kadikular.quantimium.util.SlotItemHandler;

public class QuantumSimulatorMenu extends AbstractContainerMenu {
    /** The panel is taller than vanilla's, to fit the fluid tanks under the grids (tools/gui_panels.py). */
    public static final int PLAYER_Y = 104;


    private static final int DATA_COUNT = 22;

    private final QuantumSimulatorBlockEntity blockEntity;
    private final ContainerData data;

    public QuantumSimulatorMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (QuantumSimulatorBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    public QuantumSimulatorMenu(int containerId, Inventory playerInv, QuantumSimulatorBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.QUANTUM_SIMULATOR_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        checkContainerDataCount(data, DATA_COUNT);

        // 3x3 Inputs (0-8)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(blockEntity.getInventory(), col + row * 3, 12 + col * 18, 18 + row * 18));
            }
        }

        // 3x3 Outputs (9-17)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(blockEntity.getInventory(), 9 + col + row * 3, 116 + col * 18, 18 + row * 18));
            }
        }

        // Target Display Slot 18 (Locked)
        this.addSlot(new SlotItemHandler(blockEntity.getInventory(), 18, 80, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }

            @Override
            public boolean mayPickup(Player playerIn) { return false; }
        });

        // Player Inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }

        // Player Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, PLAYER_Y + 58));
        }

        addDataSlots(data);
    }

    public QuantumSimulatorBlockEntity getBlockEntity() { return blockEntity; }
    public boolean isEngaged() { return data.get(6) == 1; }
    public int getProgress() { return data.get(0); }
    public int getMaxProgress() { return data.get(1); }
    public int getEnergyStored() { return (data.get(3) << 16) | (data.get(2) & 0xFFFF); }
    public int getMaxEnergyStored() { return (data.get(5) << 16) | (data.get(4) & 0xFFFF); }
    public int getSimulationMode() { return data.get(8); } // 0 = RECIPE, 1 = PARALLEL
    public int getStatusCode() { return data.get(9); }
    /** Bit per 3x3 input slot; set bits are locked by the current slot mapping. */
    public int getLockedMask() { return data.get(10); }
    /** The batch chosen in the GUI. */
    public int getBatchSize() { return data.get(11); }
    /** The batch that runs: the one chosen, capped by the flux band here. */
    public int getRunningBatch() { return data.get(19); }
    public FluxBand getBand() { return FluxBand.values()[Math.clamp(data.get(20), 0, FluxBand.values().length - 1)]; }
    public FluxBand getHeading() { return FluxBand.values()[Math.clamp(data.get(21), 0, FluxBand.values().length - 1)]; }
    public int getCurrentPowerUse() { return (data.get(13) << 16) | (data.get(12) & 0xFFFF); }
    public int getAveragePowerUse() { return (data.get(15) << 16) | (data.get(14) & 0xFFFF); }

    public int getAnomalySurchargePercent() { return data.get(16); }

    public FluxBand getAnomalyBand() {
        int ordinal = data.get(17);
        FluxBand[] bands = FluxBand.values();
        if (ordinal < 0 || ordinal >= bands.length) return FluxBand.LOW;
        return bands[ordinal];
    }

    public int getPassiveDrainFePerTick() { return data.get(18); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            if (index < 19) { // Container slots
                if (!this.moveItemStackTo(stackInSlot, 19, 55, true)) return ItemStack.EMPTY;
            } else { // Player inventory slots
                if (!this.moveItemStackTo(stackInSlot, 0, 9, false)) return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();

            if (stackInSlot.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stackInSlot);
        }
        return itemstack;
    }
    
    @Override
    public boolean stillValid(Player player) {
        Level level = this.blockEntity.getLevel() != null ? this.blockEntity.getLevel() : player.level();
        return stillValid(ContainerLevelAccess.create(level, this.blockEntity.getBlockPos()), player, ModBlocks.QUANTUM_SIMULATOR.get());
    }
}