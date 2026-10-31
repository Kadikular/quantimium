package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.ReactorLogisticsPortBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Logistics Receptacle: its filter's nine ghost entries and the player's inventory; its role, active or passive,
 * priority and allow/deny on the screen's tabs. Layout: tools/gui_panels.py logistics_port.
 */
public class ReactorLogisticsPortMenu extends QuantumMenu implements GhostFilterMenu, ChannelMenu {

    public static final int BUTTON_STORING = 0;
    public static final int BUTTON_SHARING = 1;
    public static final int BUTTON_ALLOW = 2;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 35;
    public static final int FILTER_START = 0;
    public static final int FILTER_END = ReactorLogisticsPortBlockEntity.FILTER_SLOTS;

    private final ReactorLogisticsPortBlockEntity port;

    public ReactorLogisticsPortMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (ReactorLogisticsPortBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public ReactorLogisticsPortMenu(int containerId, Inventory playerInv, ReactorLogisticsPortBlockEntity port) {
        super(ModMenuTypes.REACTOR_LOGISTICS_PORT_MENU.get(), containerId);
        this.port = port;
        addDataSlot(channelSlot = new ChannelMenu.ChannelSlot(port.getLevel() != null && port.getLevel().isClientSide() ? null : port::channel));
        for (int i = 0; i < ReactorLogisticsPortBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new GhostSlot(port.getFilter(), i, FILTER_X + i * 18, FILTER_Y));
        }
        addPlayerInventory(playerInv, PLAYER_INVENTORY_Y);
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

    public ReactorLogisticsPortBlockEntity getBlockEntity() {
        return port;
    }

    /** A filter entry takes what's on the cursor, or clears with an empty hand; shift-click steps it through its item's tags. */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= FILTER_START && slotId < FILTER_END) {
            if (clickType == ContainerInput.QUICK_MOVE) port.cycleFilterTag(slotId);
            else if (clickType == ContainerInput.PICKUP) setGhost(slotId, getCarried());
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
        if (PriorityButton.is(id)) {
            port.addPriority(PriorityButton.decode(id) - port.priority());
            return true;
        }
        switch (id) {
            case BUTTON_STORING -> port.toggleStoring();
            case BUTTON_SHARING -> port.toggleSharing();
            case BUTTON_ALLOW -> port.toggleAllow();
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
        for (int i = 0; i < ReactorLogisticsPortBlockEntity.FILTER_SLOTS; i++) {
            if (port.getFilter().getItem(i).isEmpty()) {
                setGhost(i, stack);
                break;
            }
        }
        return ItemStack.EMPTY;
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

    @Override
    public boolean allowsAll() {
        return false;
    }
}
