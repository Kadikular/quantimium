package com.kadikular.quantimium.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.IntPredicate;

/**
 * A machine's slots as pipes and hoppers see them: the first {@code slots} of its inventory, each taking items in or
 * giving them out only as the machine's rules say (a face's side settings, a slot locked, a pillar missing). What
 * may go into a slot is also what the inventory itself accepts there.
 */
public final class GatedItems implements IItemHandler {

    private final ItemStackHandler inventory;
    private final int slots;
    private final IntPredicate takesIn;
    private final IntPredicate givesOut;

    /**
     * @param takesIn  whether a slot may be filled from outside now
     * @param givesOut whether a slot may be emptied from outside now
     */
    public GatedItems(ItemStackHandler inventory, int slots, IntPredicate takesIn, IntPredicate givesOut) {
        this.inventory = inventory;
        this.slots = slots;
        this.takesIn = takesIn;
        this.givesOut = givesOut;
    }

    @Override
    public int getSlots() {
        return slots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return takesIn.test(slot) ? inventory.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return givesOut.test(slot) ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return takesIn.test(slot) && inventory.isItemValid(slot, stack);
    }
}
