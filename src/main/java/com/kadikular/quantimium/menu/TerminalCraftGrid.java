package com.kadikular.quantimium.menu;

import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A Logistics Terminal's 3x3 crafting grid over {@code items}: on the server the terminal's own, so it's kept while
 * no one has it open and every player at that terminal sees the one grid; on the client a copy. {@code changed}
 * hears every change.
 */
public final class TerminalCraftGrid implements CraftingContainer {
    private final List<ItemStack> items;
    private final Runnable changed;

    public TerminalCraftGrid(List<ItemStack> items, Runnable changed) {
        this.items = items;
        this.changed = changed;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public List<ItemStack> getItems() {
        return List.copyOf(items);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) changed.run();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        changed.run();
    }

    @Override
    public void setChanged() {
        changed.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        changed.run();
    }

    @Override
    public void fillStackedContents(StackedItemContents contents) {
        for (ItemStack stack : items) contents.accountSimpleStack(stack);
    }
}
