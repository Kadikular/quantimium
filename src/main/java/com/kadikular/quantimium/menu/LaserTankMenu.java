package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.LaserTankBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A Laser Tank: its four tanks, drawn by the screen from the block entity, and the player's inventory. */
public class LaserTankMenu extends AbstractContainerMenu {

    /** Collect, send or both. */
    public static final int BUTTON_MODE = 0;

    private final LaserTankBlockEntity tank;

    public LaserTankMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (LaserTankBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public LaserTankMenu(int containerId, Inventory playerInv, LaserTankBlockEntity tank) {
        super(ModMenuTypes.LASER_TANK_MENU.get(), containerId);
        this.tank = tank;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public LaserTankBlockEntity getBlockEntity() {
        return tank;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_MODE && tank != null) {
            tank.cycleMode();
            return true;
        }
        return false;
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
