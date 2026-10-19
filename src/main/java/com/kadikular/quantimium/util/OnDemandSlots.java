package com.kadikular.quantimium.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A slot-based view whose pulls from some slots do work rather than just move items: a Quantum
 * Crafter's output shows what a pull would craft, and pulling it runs the craft (energy, flux,
 * ingredients). {@link LegacyItems} runs such a pull inside the caller's transaction, with the machine's
 * state snapshotted, so a transaction that only asks ("simulate") is undone in full.
 */
public interface OnDemandSlots {
    /** Whether pulling {@code slot} now would do work rather than hand over items already there. */
    boolean producesOnDemand(int slot);

    /**
     * What a pull's work changes besides the inventories (power, a reserve), so an aborted transaction can put it
     * back: taken before a pull in a transaction, given back to {@link #restoreState} if it aborts.
     */
    @org.jspecify.annotations.Nullable
    default Object saveState() {
        return null;
    }

    default void restoreState(@org.jspecify.annotations.Nullable Object state) {}

    /**
     * Its inventory is about to be put back as it was ({@code true}), or has been ({@code false}): while it is,
     * the transaction is closing and nothing may be read from or moved in another inventory, so it should not
     * react to the change.
     */
    default void restoring(boolean restoring) {}

    /** The transaction its pulls were made in has committed: what waited on that (flux) happens now. */
    default void committed() {}

    /**
     * The same slots with only what's really in them: what an on-demand slot would make reads as empty and can't be
     * pulled. For a block asked with no face: inventory viewers and other mods' screens, which show and move the
     * slots as a chest's and would run a craft just by looking to clear a slot. Keep one per block entity.
     */
    final class RealOnly implements IItemHandler {
        private final IItemHandler view;

        public RealOnly(IItemHandler view) {
            this.view = view;
        }

        private boolean onDemand(int slot) {
            return view instanceof OnDemandSlots slots && slots.producesOnDemand(slot);
        }

        @Override
        public int getSlots() {
            return view.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return onDemand(slot) ? ItemStack.EMPTY : view.getStackInSlot(slot);
        }

        /**
         * Into a slot that takes it; put into one that takes nothing (an output), it goes on into those that do,
         * since some screens drop whatever a slot turns away.
         */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (view.isItemValid(slot, stack)) return view.insertItem(slot, stack, simulate);
            ItemStack rest = stack;
            for (int other = 0; other < view.getSlots() && !rest.isEmpty(); other++) {
                if (other != slot && view.isItemValid(other, rest)) rest = view.insertItem(other, rest, simulate);
            }
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return onDemand(slot) ? ItemStack.EMPTY : view.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return view.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return view.isItemValid(slot, stack);
        }
    }
}
