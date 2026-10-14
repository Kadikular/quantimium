package com.kadikular.quantimium.item;

import com.kadikular.quantimium.menu.LensHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** A lens with settings of its own, set in a screen opened from the hand, a Lens Mount or a Relay emitter. */
public interface LensWithSettings {

    AbstractContainerMenu menu(int containerId, Inventory inventory, LensHolder holder);
}
