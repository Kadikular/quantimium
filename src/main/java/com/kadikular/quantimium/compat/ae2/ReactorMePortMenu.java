package com.kadikular.quantimium.compat.ae2;

import com.kadikular.quantimium.menu.GhostFilterMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The ME Superposition Port's screen: its mode, priority and whether the network may store items in
 * the Reactor, or its filter's 27 ghost slots, one list at a time, and the player's inventory. The
 * slots of whichever view is hidden stay out of reach. Layout: tools/gui_panels.py reactor_me_port.
 */
public class ReactorMePortMenu extends AbstractContainerMenu implements GhostFilterMenu {

    public static final int BUTTON_MODE_NEXT = 0;
    public static final int BUTTON_MODE_BACK = 1;
    public static final int BUTTON_ACCEPTS = 2;
    public static final int BUTTON_PATTERNS_ALLOW = 3;
    public static final int BUTTON_STORAGE_ALLOW = 4;
    /** Priority steps, as AE2's own priority screen has them: buttons 10 to 15. */
    public static final int[] PRIORITY_STEPS = {-100, -10, -1, 1, 10, 100};
    public static final int BUTTON_PRIORITY = 10;

    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 18;
    public static final int FILTER_START = 0;
    public static final int FILTER_END = FILTER_START + ReactorMePortBlockEntity.FILTER_SLOTS * 2;

    private final ReactorMePortBlockEntity port;
    private final ContainerData data;
    /** Client only: whether the screen is showing the filter, and which of its two lists. */
    private boolean filterView;
    private boolean storageList;

    public ReactorMePortMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (ReactorMePortBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(ReactorMePortBlockEntity.DATA_COUNT));
    }

    public ReactorMePortMenu(int containerId, Inventory playerInv, ReactorMePortBlockEntity port) {
        this(containerId, playerInv, port, port.data);
    }

    private ReactorMePortMenu(int containerId, Inventory playerInv, ReactorMePortBlockEntity port, ContainerData data) {
        super(Ae2Content.REACTOR_ME_PORT_MENU.get(), containerId);
        this.port = port;
        this.data = data;
        checkContainerDataCount(data, ReactorMePortBlockEntity.DATA_COUNT);
        SimpleContainer filter = port.getFilter();
        for (int i = 0; i < ReactorMePortBlockEntity.FILTER_SLOTS * 2; i++) {
            int spot = i % ReactorMePortBlockEntity.FILTER_SLOTS;
            addSlot(new GhostSlot(filter, i, FILTER_X + (spot % 9) * 18, FILTER_Y + (spot / 9) * 18,
                    i >= ReactorMePortBlockEntity.STORAGE_FILTER_START));
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
        private final boolean storage;

        GhostSlot(Container container, int index, int x, int y, boolean storage) {
            super(container, index, x, y);
            this.storage = storage;
        }

        @Override
        public boolean isActive() {
            return filterView && storage == storageList;
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

    public ReactorMePortBlockEntity getBlockEntity() {
        return port;
    }

    public boolean isFilterView() {
        return filterView;
    }

    public void setFilterView(boolean filterView) {
        this.filterView = filterView;
    }

    public boolean isStorageList() {
        return storageList;
    }

    public void setStorageList(boolean storageList) {
        this.storageList = storageList;
    }

    /** 0: not part of a Reactor; 1: no network or channel; 2: online. */
    public int link() {
        return data.get(0);
    }

    public int shownKinds() {
        return data.get(1);
    }

    public int patternCount() {
        return data.get(2);
    }

    public int priority() {
        return (short) data.get(4) << 16 | (data.get(3) & 0xFFFF);
    }

    public ReactorMePortBlockEntity.Mode mode() {
        ReactorMePortBlockEntity.Mode[] all = ReactorMePortBlockEntity.Mode.values();
        return all[Math.clamp(data.get(5), 0, all.length - 1)];
    }

    public boolean patternsAllow() {
        return (data.get(6) & 1) != 0;
    }

    public boolean storageAllow() {
        return (data.get(6) & 2) != 0;
    }

    public boolean acceptsItems() {
        return (data.get(6) & 4) != 0;
    }

    /**
     * Clicking a filter entry sets it to what is on the cursor, or clears it with an empty hand;
     * shift-clicking steps it through its item's tags.
     */
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
        switch (id) {
            case BUTTON_MODE_NEXT -> port.cycleMode(false);
            case BUTTON_MODE_BACK -> port.cycleMode(true);
            case BUTTON_ACCEPTS -> port.toggleAcceptsItems();
            case BUTTON_PATTERNS_ALLOW -> port.togglePatternsAllow();
            case BUTTON_STORAGE_ALLOW -> port.toggleStorageAllow();
            default -> {
                int step = id - BUTTON_PRIORITY;
                if (step < 0 || step >= PRIORITY_STEPS.length) return false;
                port.addPriority(PRIORITY_STEPS[step]);
            }
        }
        return true;
    }

    /** Nothing to move: the filter's slots are ghosts. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(port.getLevel(), port.getBlockPos()), player,
                Ae2Content.REACTOR_ME_PORT.get());
    }
}
