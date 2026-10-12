package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
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
 * A Logistics Receptacle: its filter's nine ghost entries and the player's inventory; its role, active or passive,
 * priority and allow/deny on the screen's tabs. Layout: tools/gui_panels.py logistics_receptacle.
 */
public class LogisticsReceptacleMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int BUTTON_ROLE = 0;
    public static final int BUTTON_ACTIVE = 1;
    public static final int BUTTON_ALLOW = 2;
    public static final int BUTTON_PRIORITY_UP = 3;
    public static final int BUTTON_PRIORITY_DOWN = 4;
    public static final int BUTTON_PRIORITY_UP_TEN = 5;
    public static final int BUTTON_PRIORITY_DOWN_TEN = 6;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 35;
    public static final int FILTER_START = 0;
    public static final int FILTER_END = LogisticsReceptacleBlockEntity.FILTER_SLOTS;

    private final LogisticsReceptacleBlockEntity receptacle;

    public LogisticsReceptacleMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (LogisticsReceptacleBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public LogisticsReceptacleMenu(int containerId, Inventory playerInv, LogisticsReceptacleBlockEntity receptacle) {
        super(ModMenuTypes.LOGISTICS_RECEPTACLE_MENU.get(), containerId);
        this.receptacle = receptacle;
        for (int i = 0; i < LogisticsReceptacleBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new GhostSlot(receptacle.getFilter(), i, FILTER_X + i * 18, FILTER_Y));
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

    private static final class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
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

    public LogisticsReceptacleBlockEntity getBlockEntity() {
        return receptacle;
    }

    /** A filter entry takes what's on the cursor, or clears with an empty hand; shift-click steps it through its item's tags. */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= FILTER_START && slotId < FILTER_END) {
            if (clickType == ContainerInput.QUICK_MOVE) receptacle.cycleFilterTag(slotId);
            else if (clickType == ContainerInput.PICKUP) setGhost(slotId, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        receptacle.setFilterSlot(index, stack);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_ROLE -> receptacle.cycleRole();
            case BUTTON_ACTIVE -> receptacle.toggleActive();
            case BUTTON_ALLOW -> receptacle.toggleAllow();
            case BUTTON_PRIORITY_UP -> receptacle.addPriority(1);
            case BUTTON_PRIORITY_DOWN -> receptacle.addPriority(-1);
            case BUTTON_PRIORITY_UP_TEN -> receptacle.addPriority(10);
            case BUTTON_PRIORITY_DOWN_TEN -> receptacle.addPriority(-10);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Shift-clicking an item in the inventory lists it in the first free entry. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < FILTER_END) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < LogisticsReceptacleBlockEntity.FILTER_SLOTS; i++) {
            if (receptacle.getFilter().getItem(i).isEmpty()) {
                setGhost(i, stack);
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(receptacle.getLevel(), receptacle.getBlockPos()), player, receptacle.getBlockState().getBlock());
    }
}
