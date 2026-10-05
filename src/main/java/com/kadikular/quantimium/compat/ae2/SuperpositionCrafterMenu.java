package com.kadikular.quantimium.compat.ae2;

import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.menu.GhostFilterMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.kadikular.quantimium.util.SlotItemHandler;

/**
 * The catalyst slot, 27 ghost filter slots, and the player's inventory. The screen shows either the
 * catalyst and its readout or the filter; the slots of whichever is hidden stay out of reach.
 */
public class SuperpositionCrafterMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int DATA_COUNT = 18;
    public static final int BUTTON_MODE = 0;
    public static final int BUTTON_BATCH = 1;
    public static final int BUTTON_INPUT_MODE = 2;

    /** Where the catalyst socket and the filter grid sit (tools/gui_panels.py). */
    public static final int CATALYST_X = 26;
    public static final int CATALYST_Y = 36;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 18;

    public static final int CATALYST_SLOT = 0;
    public static final int FILTER_START = 1;
    /** Output entries, then input entries, all drawn at the same 27 spots; the screen shows one list. */
    public static final int FILTER_END = FILTER_START + SuperpositionCrafterBlockEntity.FILTER_SLOTS * 2;

    private final SuperpositionCrafterBlockEntity blockEntity;
    private final ContainerData data;
    /** Client only: whether the screen is showing the filter, and which of its two lists. */
    private boolean filterView;
    private boolean inputList;

    public SuperpositionCrafterMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (SuperpositionCrafterBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public SuperpositionCrafterMenu(int containerId, Inventory playerInv, SuperpositionCrafterBlockEntity blockEntity,
                                    ContainerData data) {
        super(Ae2Content.SUPERPOSITION_CRAFTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);

        addSlot(new SlotItemHandler(blockEntity.getCatalyst(), 0, CATALYST_X, CATALYST_Y) {
            @Override
            public boolean isActive() {
                return !filterView;
            }
        });
        SimpleContainer filter = blockEntity.getFilter();
        for (int i = 0; i < SuperpositionCrafterBlockEntity.FILTER_SLOTS * 2; i++) {
            int spot = i % SuperpositionCrafterBlockEntity.FILTER_SLOTS;
            addSlot(new GhostSlot(filter, i, FILTER_X + (spot % 9) * 18, FILTER_Y + (spot / 9) * 18,
                    i >= SuperpositionCrafterBlockEntity.INPUT_FILTER_START));
        }
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

    private final class GhostSlot extends Slot {
        private final boolean input;

        GhostSlot(Container container, int index, int x, int y, boolean input) {
            super(container, index, x, y);
            this.input = input;
        }

        @Override
        public boolean isActive() {
            return filterView && input == inputList;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    public SuperpositionCrafterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isFilterView() {
        return filterView;
    }

    public void setFilterView(boolean filterView) {
        this.filterView = filterView;
    }

    public boolean isInputList() {
        return inputList;
    }

    public void setInputList(boolean inputList) {
        this.inputList = inputList;
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

    public int getFilterMode() {
        return data.get(5);
    }

    public int getRecipeCount() {
        return data.get(6);
    }

    public int getRunsPerSecond() {
        return data.get(7);
    }

    public int getInputMode() {
        return data.get(11);
    }

    public int getSurchargePercent() {
        return data.get(12);
    }

    public FluxBand getAnomalyBand() {
        FluxBand[] bands = FluxBand.values();
        return bands[Math.clamp(data.get(13), 0, bands.length - 1)];
    }

    public int getPassiveDrain() {
        return data.get(14);
    }

    public int getBatch() {
        return data.get(10);
    }

    public int getAverageFe() {
        return (data.get(9) << 16) | (data.get(8) & 0xFFFF);
    }

    /**
     * Clicking a filter entry sets it to what is on the cursor, or clears it with an empty hand;
     * shift-clicking steps it through its item's tags.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= FILTER_START && slotId < FILTER_END) {
            int index = slotId - FILTER_START;
            if (clickType == ContainerInput.QUICK_MOVE) blockEntity.cycleFilterTag(index);
            else if (clickType == ContainerInput.PICKUP) setGhost(index, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        blockEntity.setFilterSlot(index, stack);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_MODE) {
            blockEntity.setFilterMode(blockEntity.getFilterMode() == SuperpositionCrafterBlockEntity.MODE_ALLOW
                    ? SuperpositionCrafterBlockEntity.MODE_DENY : SuperpositionCrafterBlockEntity.MODE_ALLOW);
            return true;
        }
        if (id == BUTTON_INPUT_MODE) {
            blockEntity.setInputMode(blockEntity.getInputMode() == SuperpositionCrafterBlockEntity.MODE_ALLOW
                    ? SuperpositionCrafterBlockEntity.MODE_DENY : SuperpositionCrafterBlockEntity.MODE_ALLOW);
            return true;
        }
        if (id == BUTTON_BATCH) {
            blockEntity.cycleBatch();
            return true;
        }
        return false;
    }

    /** Shift-click moves a catalyst in or out; ghost slots never hold anything to move. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || (index >= FILTER_START && index < FILTER_END)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == CATALYST_SLOT) {
            if (!moveItemStackTo(stack, FILTER_END, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, CATALYST_SLOT, CATALYST_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, Ae2Content.SUPERPOSITION_CRAFTER.get());
    }

    /** The flux band here, where it's heading, and the band the catalyst in the slot needs. */
    public FluxBand getBand() {
        return band(15);
    }

    public FluxBand getHeading() {
        return band(16);
    }

    public FluxBand getRequiredBand() {
        return band(17);
    }

    private FluxBand band(int index) {
        return FluxBand.values()[Math.clamp(data.get(index), 0, FluxBand.values().length - 1)];
    }
}
