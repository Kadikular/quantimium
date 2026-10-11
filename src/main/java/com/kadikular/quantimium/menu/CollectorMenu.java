package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.CollectorBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.util.SlotItemHandler;
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
 * A Collector: what it's caught in a 3x3 grid, as a dispenser's, or its filter's nine ghost entries,
 * one view at a time, and the player's inventory. Layout: tools/gui_panels.py collector.
 */
public class CollectorMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int BUTTON_MODE = 0;
    /** Collect, send or both. */
    public static final int BUTTON_RELAY = 1;
    public static final int GRID_X = 62;
    public static final int GRID_Y = 17;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 35;

    public static final int ITEMS_START = 0;
    public static final int FILTER_START = CollectorBlockEntity.SLOTS;
    public static final int FILTER_END = FILTER_START + CollectorBlockEntity.FILTER_SLOTS;

    private final CollectorBlockEntity collector;
    /** Client only: whether the screen is showing the filter. */
    private boolean filterView;

    public CollectorMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (CollectorBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public CollectorMenu(int containerId, Inventory playerInv, CollectorBlockEntity collector) {
        super(ModMenuTypes.COLLECTOR_MENU.get(), containerId);
        this.collector = collector;
        for (int i = 0; i < CollectorBlockEntity.SLOTS; i++) {
            addSlot(new SlotItemHandler(collector.getItems(), i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18) {
                @Override
                public boolean isActive() {
                    return !filterView;
                }
            });
        }
        for (int i = 0; i < CollectorBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new GhostSlot(collector.getFilter(), i, FILTER_X + i * 18, FILTER_Y));
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
        GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return filterView;
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

    public CollectorBlockEntity getBlockEntity() {
        return collector;
    }

    public boolean isFilterView() {
        return filterView;
    }

    public void setFilterView(boolean filterView) {
        this.filterView = filterView;
    }

    /** A filter entry takes what's on the cursor, or clears with an empty hand; shift-click steps it through its item's tags. */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= FILTER_START && slotId < FILTER_END) {
            int index = slotId - FILTER_START;
            if (clickType == ContainerInput.QUICK_MOVE) collector.cycleFilterTag(index);
            else if (clickType == ContainerInput.PICKUP) setGhost(index, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        collector.setFilterSlot(index, stack);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_MODE) {
            collector.toggleMode();
            return true;
        }
        if (id == BUTTON_RELAY) {
            collector.cycleMode();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || (index >= FILTER_START && index < FILTER_END)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < FILTER_START) {
            if (!moveItemStackTo(stack, FILTER_END, slots.size(), true)) return ItemStack.EMPTY;
        } else if (filterView || !moveItemStackTo(stack, ITEMS_START, FILTER_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(collector.getLevel(), collector.getBlockPos()), player, collector.getBlockState().getBlock());
    }
}
