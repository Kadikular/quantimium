package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.LaserTankBlockEntity;
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
 * A Laser Tank: its four tanks (drawn by the screen from the block entity), or its filter's nine fluid entries, one view
 * at a time, and the player's inventory. A bucket clicked on a tank fills or empties it.
 */
public class LaserTankMenu extends AbstractContainerMenu implements GhostFilterMenu {

    /** Collect, send or both. */
    public static final int BUTTON_MODE = 0;
    /** The filter between allow and deny. */
    public static final int BUTTON_ALLOW = 1;
    /** A bucket on the cursor clicked on tank (this + its index). */
    public static final int BUTTON_TANK = 10;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 35;
    public static final int FILTER_END = LaserTankBlockEntity.FILTER_SLOTS;

    private final LaserTankBlockEntity tank;
    /** Client only: whether the screen is showing the filter. */
    private boolean filterView;

    public LaserTankMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (LaserTankBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public LaserTankMenu(int containerId, Inventory playerInv, LaserTankBlockEntity tank) {
        super(ModMenuTypes.LASER_TANK_MENU.get(), containerId);
        this.tank = tank;
        for (int i = 0; i < LaserTankBlockEntity.FILTER_SLOTS; i++) {
            addSlot(new GhostSlot(tank.getFilter(), i, FILTER_X + i * 18, FILTER_Y));
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

    public LaserTankBlockEntity getBlockEntity() {
        return tank;
    }

    public boolean isFilterView() {
        return filterView;
    }

    public void setFilterView(boolean filterView) {
        this.filterView = filterView;
    }

    /** A filter entry lists the fluid in what's on the cursor, or clears with an empty hand. */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < FILTER_END) {
            if (clickType == ContainerInput.PICKUP) setGhost(slotId, getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void setGhost(int index, ItemStack stack) {
        tank.setFilterSlot(index, stack);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (tank == null) return false;
        if (id == BUTTON_MODE) {
            tank.cycleMode();
            return true;
        }
        if (id == BUTTON_ALLOW) {
            tank.toggleAllow();
            return true;
        }
        if (id >= BUTTON_TANK && id < BUTTON_TANK + LaserTankBlockEntity.TANKS) return pour(player, id - BUTTON_TANK);
        return false;
    }

    /** The cursor's bucket on tank {@code index}: filled from it if it can be, else emptied into it. */
    private boolean pour(Player player, int index) {
        var held = net.neoforged.neoforge.transfer.access.ItemAccess.forPlayerCursor(player, this).oneByOne()
                .getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM);
        if (held == null) return false;
        var into = tank.tanks().tank(index);
        boolean moved = net.neoforged.neoforge.transfer.ResourceHandlerUtil.moveFirst(into, held, fluid -> true, Integer.MAX_VALUE, null) != null
                || net.neoforged.neoforge.transfer.ResourceHandlerUtil.moveFirst(held, into, fluid -> true, Integer.MAX_VALUE, null) != null;
        if (moved) broadcastChanges();
        return moved;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return tank != null && stillValid(ContainerLevelAccess.create(tank.getLevel(), tank.getBlockPos()), player, tank.getBlockState().getBlock());
    }
}
