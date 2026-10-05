package com.kadikular.quantimium.superposition;

import net.minecraft.world.entity.ContainerUser;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A player's stash as a chest to open: a copy of what the registry holds, written back to it whenever
 * it changes, so the stash is the same whichever pod (or wherever) it is opened from.
 */
public final class StashContainer extends SimpleContainer {

    private final NonNullList<ItemStack> stored;

    public StashContainer(NonNullList<ItemStack> stored) {
        super(stored.size());
        this.stored = stored;
        for (int slot = 0; slot < stored.size(); slot++) super.setItem(slot, stored.get(slot).copy());
    }

    @Override
    public void setChanged() {
        super.setChanged();
        for (int slot = 0; slot < stored.size(); slot++) stored.set(slot, getItem(slot).copy());
    }

    @Override
    public void stopOpen(ContainerUser user) {
        super.stopOpen(user);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
