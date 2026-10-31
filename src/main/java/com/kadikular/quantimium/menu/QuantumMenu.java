package com.kadikular.quantimium.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * A Quantimium screen's menu: what all of them share. The player's inventory sits where the panels in
 * tools/gui_panels.py draw it: three rows of nine from x 8, and the hotbar {@value #HOTBAR_GAP} below the top row.
 */
public abstract class QuantumMenu extends AbstractContainerMenu {

    /** The top row of the player's inventory on most panels. */
    public static final int PLAYER_INVENTORY_Y = 84;
    /** From the inventory's top row to the hotbar. */
    public static final int HOTBAR_GAP = 58;

    protected QuantumMenu(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /** Adds the player's 27 inventory slots in three rows from {@code top}, then the hotbar's nine. */
    protected void addPlayerInventory(Inventory inventory, int top) {
        addPlayerInventory(inventory, top, -1);
    }

    /**
     * As {@link #addPlayerInventory(Inventory, int)}, but the item in hotbar slot {@code heldSlot} (a lens this screen
     * sets, held) stays where it is; -1 for none.
     */
    protected void addPlayerInventory(Inventory inventory, int top, int heldSlot) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, top + HOTBAR_GAP) {
                @Override
                public boolean mayPickup(Player who) {
                    return getContainerSlot() != heldSlot && super.mayPickup(who);
                }
            });
        }
    }
}
