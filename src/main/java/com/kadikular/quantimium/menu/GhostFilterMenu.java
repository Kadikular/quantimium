package com.kadikular.quantimium.menu;

import net.minecraft.world.item.ItemStack;

/**
 * A menu with ghost filter slots that a recipe viewer can drop items onto. Lives outside the AE2
 * package so the packet that carries the drop can be registered whether AE2 is installed or not.
 */
public interface GhostFilterMenu {

    /** Sets filter slot {@code index} (0-based within the filter) to a copy of {@code stack}, or clears it. */
    void setGhost(int index, ItemStack stack);
}
