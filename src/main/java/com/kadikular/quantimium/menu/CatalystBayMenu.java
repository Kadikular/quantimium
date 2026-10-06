package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Catalyst Bay: its four catalysts in a 2x2 grid, laid out as the window's quarters, or its filter's
 * 27 ghost slots, one list at a time, and the player's inventory. The slots of whichever view is hidden
 * stay out of reach. Layout: tools/gui_panels.py catalyst_bay.
 */
public class CatalystBayMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int BUTTON_OUTPUT_MODE = 0;
    public static final int BUTTON_INPUT_MODE = 1;

    /** The top-left catalyst slot: the grid sits on the left, in brackets, with the readout beside it. */
    public static final int GRID_X = 26;
    public static final int GRID_Y = 27;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 18;

    public static final int CATALYST_START = 0;
    public static final int FILTER_START = CATALYST_START + CatalystBayBlockEntity.SLOTS;
    /** Output entries, then input entries, all drawn at the same 27 spots; the screen shows one list. */
    public static final int FILTER_END = FILTER_START + CatalystBayBlockEntity.FILTER_SLOTS * 2;

    private final CatalystBayBlockEntity bay;
    /** Client only: whether the screen is showing the filter, and which of its two lists. */
    private boolean filterView;
    private boolean inputList;

    public CatalystBayMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (CatalystBayBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public CatalystBayMenu(int containerId, Inventory playerInv, CatalystBayBlockEntity bay) {
        super(ModMenuTypes.CATALYST_BAY_MENU.get(), containerId);
        this.bay = bay;
        for (int i = 0; i < CatalystBayBlockEntity.SLOTS; i++) {
            addSlot(new Slot(bay.getCatalystContainer(), i, GRID_X + (i % 2) * 18, GRID_Y + (i / 2) * 18) {
                @Override
                public boolean isActive() {
                    return !filterView;
                }
            });
        }
        for (int i = 0; i < CatalystBayBlockEntity.FILTER_SLOTS * 2; i++) {
            int spot = i % CatalystBayBlockEntity.FILTER_SLOTS;
            addSlot(new GhostSlot(bay.getFilter(), i, FILTER_X + (spot % 9) * 18, FILTER_Y + (spot / 9) * 18,
                    i >= CatalystBayBlockEntity.INPUT_FILTER_START));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
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

    public CatalystBayBlockEntity getBlockEntity() {
        return bay;
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

    /**
     * Clicking a filter entry sets it to what is on the cursor, or clears it with an empty hand;
     * shift-clicking steps it through its item's tags.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= FILTER_START && slotId < FILTER_END) {
            int index = slotId - FILTER_START;
            if (clickType == ContainerInput.QUICK_MOVE) bay.cycleFilterTag(index);
            else if (clickType == ContainerInput.PICKUP) setGhost(index, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        bay.setFilterSlot(index, stack);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_OUTPUT_MODE) {
            bay.toggleOutputMode();
            return true;
        }
        if (id == BUTTON_INPUT_MODE) {
            bay.toggleInputMode();
            return true;
        }
        return false;
    }

    /** Shift-click moves a catalyst in or out, one at a time; ghost slots never hold anything to move. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || (index >= FILTER_START && index < FILTER_END)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < FILTER_START) {
            if (!moveItemStackTo(stack, FILTER_END, slots.size(), true)) return ItemStack.EMPTY;
        } else if (filterView || !moveItemStackTo(stack, CATALYST_START, FILTER_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(bay.getLevel(), bay.getBlockPos()), player,
                ModBlocks.CATALYST_BAY.get());
    }
}
