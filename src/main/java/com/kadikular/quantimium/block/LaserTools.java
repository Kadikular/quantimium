package com.kadikular.quantimium.block;

import com.kadikular.quantimium.init.ModItems;
import net.minecraft.world.item.ItemStack;

/** The items that work on laser blocks themselves, so a click with one goes to the item, not the block. */
final class LaserTools {
    private LaserTools() {}

    static boolean isOne(ItemStack stack) {
        return stack.is(ModItems.LASER_LINKER.get()) || stack.is(ModItems.SPECTROSCOPE.get());
    }
}
