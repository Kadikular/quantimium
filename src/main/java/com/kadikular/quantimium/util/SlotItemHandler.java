package com.kadikular.quantimium.util;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A menu slot backed by one slot of an {@link ItemStackHandler}. */
public class SlotItemHandler extends Slot {
    private static final Container EMPTY_CONTAINER = new SimpleContainer(0);

    private final ItemStackHandler handler;
    private final int handlerIndex;

    public SlotItemHandler(ItemStackHandler handler, int index, int x, int y) {
        super(EMPTY_CONTAINER, index, x, y);
        this.handler = handler;
        this.handlerIndex = index;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && handler.isItemValid(handlerIndex, stack);
    }

    @Override
    public ItemStack getItem() {
        return handler.getStackInSlot(handlerIndex);
    }

    @Override
    public void set(ItemStack stack) {
        handler.setStackInSlot(handlerIndex, stack);
        setChanged();
    }

    @Override
    public void setByPlayer(ItemStack stack) {
        set(stack);
    }

    @Override
    public void setByPlayer(ItemStack stack, ItemStack previous) {
        set(stack);
    }

    @Override
    public void setChanged() {}

    @Override
    public int getMaxStackSize() {
        return handler.getSlotLimit(handlerIndex);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Math.min(handler.getSlotLimit(handlerIndex), stack.getMaxStackSize());
    }

    @Override
    public boolean mayPickup(Player player) {
        return !handler.extractItem(handlerIndex, 1, true).isEmpty();
    }

    @Override
    public ItemStack remove(int amount) {
        return handler.extractItem(handlerIndex, amount, false);
    }

    @Override
    public boolean isSameInventory(Slot other) {
        return other instanceof SlotItemHandler slot && slot.handler == handler;
    }
}
