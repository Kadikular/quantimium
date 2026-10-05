package com.kadikular.quantimium.unrealised;

import net.minecraft.world.item.ItemStack;

/**
 * One form Unrealised Matter could take, and how much of it one Matter makes on average: an ore's
 * drops vary (copper ore drops two to five raw copper). The Materialiser gives the average rounded
 * down, the same every time, so what it shows is what it makes.
 *
 * @param item   the item, one of it
 * @param amount how many one Matter makes, on average
 */
public record Form(ItemStack item, double amount) {

    public Form {
        item = item.copyWithCount(1);
    }

    /** A whole stack, as a Chamber's real roll gives it. */
    public static Form of(ItemStack stack) {
        return new Form(stack, stack.getCount());
    }

    /** Whole items one Matter makes, the average rounded down: what the Materialiser gives. */
    public int whole() {
        return (int) Math.floor(amount + 1e-9);
    }

    /** The item, {@link #whole()} of it: what the Materialiser shows and makes. */
    public ItemStack stack() {
        return item.copyWithCount(Math.max(1, whole()));
    }
}
