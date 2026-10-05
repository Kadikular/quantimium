package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.kadikular.quantimium.util.SlotItemHandler;

public class QuantumCrafterMenu extends AbstractContainerMenu {

    private static final int DATA_COUNT = 19;
    public static final int OUTPUTS_PER_PAGE = 9;
    public static final int PAGE_COUNT = QuantumCrafterBlockEntity.OUTPUT_SLOTS / OUTPUTS_PER_PAGE;
    private static final int MENU_CATALYST_SLOT = 18;
    private static final int MACHINE_MENU_SLOTS = 19;

    private final QuantumCrafterBlockEntity blockEntity;
    private final ContainerData data;
    private int outputPage;
    /** Stable, synchronised slot contents used to display previews without lying about real storage. */
    private final SimpleContainer displayOutputs = new SimpleContainer(QuantumCrafterBlockEntity.INPUT_SLOTS);

    public QuantumCrafterMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (QuantumCrafterBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public QuantumCrafterMenu(int containerId, Inventory playerInv, QuantumCrafterBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.QUANTUM_CRAFTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);

        // 3x3 Inputs (0-8)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(blockEntity.getInventory(), col + row * 3, 12 + col * 18, 18 + row * 18));
            }
        }

        // 3x3 paged Outputs (menu slots 9-17) — ghost-aware
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int local = col + row * 3;
                this.addSlot(new GhostOutputSlot(this, blockEntity, displayOutputs, local,
                        116 + col * 18, 18 + row * 18));
            }
        }

        // Catalyst (menu slot 18; backing slot follows all 54 outputs)
        this.addSlot(new SlotItemHandler(blockEntity.getInventory(), QuantumCrafterBlockEntity.CATALYST_SLOT, 80, 36));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
        syncDisplayOutputs();
    }

    public QuantumCrafterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isBasic() {
        return blockEntity.isBasic();
    }

    public int getEnergyStored() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int getMaxEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getStatusCode() {
        return data.get(4);
    }

    public boolean isRecipeLocked() {
        return data.get(5) != 0;
    }

    public int getGhostCount() {
        return data.get(6);
    }

    public int getCurrentPowerUse() {
        return (data.get(8) << 16) | (data.get(7) & 0xFFFF);
    }

    public int getAveragePowerUse() {
        return (data.get(10) << 16) | (data.get(9) & 0xFFFF);
    }

    public int getAnomalySurchargePercent() {
        return data.get(11);
    }

    public FluxBand getAnomalyBand() {
        int ordinal = data.get(12);
        FluxBand[] bands = FluxBand.values();
        if (ordinal < 0 || ordinal >= bands.length) return FluxBand.LOW;
        return bands[ordinal];
    }

    public int getPassiveDrainFePerTick() {
        return data.get(13);
    }

    public int getCoherenceReserve() {
        return data.get(14);
    }

    public int getCoherenceCapacity() {
        return data.get(15);
    }

    public int getOutputPage() {
        return outputPage;
    }

    public void setOutputPage(int page) {
        outputPage = Math.max(0, Math.min(PAGE_COUNT - 1, page));
        syncDisplayOutputs();
        broadcastChanges();
    }

    public int absoluteOutputSlot(int localOutput) {
        return QuantumCrafterBlockEntity.OUTPUT_START + outputPage * OUTPUTS_PER_PAGE + localOutput;
    }

    public int absoluteOutputForMenuSlot(int menuSlot) {
        return absoluteOutputSlot(menuSlot - QuantumCrafterBlockEntity.OUTPUT_START);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        // A preview is not an inventory stack. Commit it once, then move the paid result. The old
        // generic path copied the preview straight into the player inventory without ever consuming
        // ingredients or energy.
        if (index >= QuantumCrafterBlockEntity.OUTPUT_START
                && index < QuantumCrafterBlockEntity.OUTPUT_START + OUTPUTS_PER_PAGE) {
            int absoluteOutput = absoluteOutputForMenuSlot(index);
            if (!blockEntity.getInventory().getStackInSlot(absoluteOutput).isEmpty()) {
                return moveRealOutput(player, index, absoluteOutput);
            }
            if (player.level().isClientSide()) return ItemStack.EMPTY;
            ItemStack produced = blockEntity.takeGhost(absoluteOutput);
            if (produced.isEmpty()) return ItemStack.EMPTY;
            ItemStack original = produced.copy();
            if (!moveItemStackTo(produced, MACHINE_MENU_SLOTS, slots.size(), true)
                    || !produced.isEmpty()) {
                blockEntity.storeOutput(produced);
            }
            syncDisplayOutputs();
            return original;
        }

        ItemStack stackInSlot = slot.getItem();
        itemstack = stackInSlot.copy();

        if (index < MACHINE_MENU_SLOTS) {
            if (!this.moveItemStackTo(stackInSlot, MACHINE_MENU_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Prefer catalyst if empty, else inputs.
            ItemStack catalyst = blockEntity.getInventory().getStackInSlot(QuantumCrafterBlockEntity.CATALYST_SLOT);
            if (catalyst.isEmpty()) {
                if (!this.moveItemStackTo(stackInSlot, MENU_CATALYST_SLOT,
                        MENU_CATALYST_SLOT + 1, false)) {
                    if (!this.moveItemStackTo(stackInSlot, 0, QuantumCrafterBlockEntity.INPUT_SLOTS, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!this.moveItemStackTo(stackInSlot, 0, QuantumCrafterBlockEntity.INPUT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        if (stackInSlot.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stackInSlot);
        return itemstack;
    }

    private ItemStack moveRealOutput(Player player, int menuSlot, int absoluteOutput) {
        ItemStack held = blockEntity.getInventory().getStackInSlot(absoluteOutput);
        if (held.isEmpty()) return ItemStack.EMPTY;
        ItemStack moving = held.copy();
        ItemStack original = moving.copy();
        if (!moveItemStackTo(moving, MACHINE_MENU_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        int moved = original.getCount() - moving.getCount();
        if (moved <= 0) return ItemStack.EMPTY;
        blockEntity.getInventory().extractItem(absoluteOutput, moved, false);
        syncDisplayOutputs();
        return original.copyWithCount(moved);
    }

    @Override
    public void broadcastChanges() {
        syncDisplayOutputs();
        super.broadcastChanges();
    }

    private void syncDisplayOutputs() {
        for (int local = 0; local < OUTPUTS_PER_PAGE; local++) {
            int absolute = absoluteOutputSlot(local);
            ItemStack real = blockEntity.getInventory().getStackInSlot(absolute);
            ItemStack shown = real.isEmpty() ? blockEntity.getGhost(absolute) : real;
            displayOutputs.setItem(local, shown.copy());
        }
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = this.blockEntity.getLevel() != null ? this.blockEntity.getLevel() : player.level();
        return level.getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && blockEntity.isUsableBy(player);
    }

    /**
     * Output slot that shows ghost previews and commits a craft when the ghost is taken.
     */
    private static final class GhostOutputSlot extends Slot {
        private final QuantumCrafterMenu menu;
        private final QuantumCrafterBlockEntity be;
        private final int localSlot;

        private GhostOutputSlot(QuantumCrafterMenu menu, QuantumCrafterBlockEntity be,
                                SimpleContainer display, int localSlot, int x, int y) {
            super(display, localSlot, x, y);
            this.menu = menu;
            this.be = be;
            this.localSlot = localSlot;
        }

        private int absoluteSlot() {
            return menu.absoluteOutputSlot(localSlot);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return be.mayTakeOutput(absoluteSlot());
        }

        @Override
        public ItemStack remove(int amount) {
            int absoluteSlot = absoluteSlot();
            ItemStack real = be.getInventory().getStackInSlot(absoluteSlot);
            if (!real.isEmpty()) {
                return be.getInventory().extractItem(absoluteSlot, amount, false);
            }
            if (be.getLevel() != null && !be.getLevel().isClientSide()) {
                return be.takeGhost(absoluteSlot);
            }
            return super.remove(amount);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            if (be.getLevel() != null && !be.getLevel().isClientSide()) {
                be.rebuildPreview();
            }
        }
    }

    /** The flux band here, where it's heading, and the band the catalyst in the slot needs. */
    public FluxBand getBand() {
        return band(16);
    }

    public FluxBand getHeading() {
        return band(17);
    }

    public FluxBand getRequiredBand() {
        return band(18);
    }

    private FluxBand band(int index) {
        return FluxBand.values()[Math.clamp(data.get(index), 0, FluxBand.values().length - 1)];
    }
}
