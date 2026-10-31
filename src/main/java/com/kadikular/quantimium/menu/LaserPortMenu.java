package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.LaserPortBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.util.SlotItemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Laser Port: what it holds in a 3x3 grid, as a dispenser's, or its filter's nine ghost entries,
 * one view at a time, and the player's inventory. Layout: tools/gui_panels.py laser_port.
 */
public class LaserPortMenu extends QuantumMenu implements GhostFilterMenu, ChannelMenu {

    public static final int BUTTON_MODE = 0;
    /** Collect, send or both. */
    public static final int BUTTON_RELAY = 1;
    public static final int GRID_X = 62;
    public static final int GRID_Y = 17;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 35;

    public static final int ITEMS_START = 0;
    public static final int FILTER_START = LaserPortBlockEntity.SLOTS;
    public static final int FILTER_END = FILTER_START + LaserPortBlockEntity.FILTER_SLOTS;

    private final LaserPortBlockEntity port;
    /** Client only: whether the screen is showing the filter. */
    private boolean filterView;

    public LaserPortMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (LaserPortBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public LaserPortMenu(int containerId, Inventory playerInv, LaserPortBlockEntity port) {
        super(ModMenuTypes.LASER_PORT_MENU.get(), containerId);
        this.port = port;
        addDataSlot(channelSlot = new ChannelMenu.ChannelSlot(port.getLevel() != null && port.getLevel().isClientSide() ? null : port::channel));
        for (int i = 0; i < LaserPortBlockEntity.SLOTS; i++) {
            addSlot(new SlotItemHandler(port.getItems(), i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18) {
                @Override
                public boolean isActive() {
                    return !filterView;
                }
            });
        }
        for (int i = 0; i < LaserPortBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new GhostSlot(port.getFilter(), i, FILTER_X + i * 18, FILTER_Y));
        }
        addPlayerInventory(playerInv, PLAYER_INVENTORY_Y);
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

    public LaserPortBlockEntity getBlockEntity() {
        return port;
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
            if (clickType == ContainerInput.QUICK_MOVE) port.cycleFilterTag(index);
            else if (clickType == ContainerInput.PICKUP) setGhost(index, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        port.setFilterSlot(index, stack);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_MODE) {
            port.toggleAllow();
            return true;
        }
        if (id == BUTTON_RELAY) {
            port.cycleMode();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= FILTER_START && index < FILTER_END) return ItemStack.EMPTY;
        return shiftClick(player, index, stack -> index < FILTER_START
                ? moveItemStackTo(stack, FILTER_END, slots.size(), true)
                : !filterView && moveItemStackTo(stack, ITEMS_START, FILTER_START, false));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(port.getLevel(), port.getBlockPos()), player, port.getBlockState().getBlock());
    }

    private ChannelMenu.ChannelSlot channelSlot;

    @Override
    public int channel() {
        return channelSlot.shown();
    }

    @Override
    public void setChannel(int channel) {
        port.setChannel(channel);
    }
}
