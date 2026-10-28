package com.kadikular.quantimium.item;

import com.kadikular.quantimium.recipe.FluidEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A filter entry standing for a fluid. Never held; named after its fluid, drawn as it by the screens. */
public class FluidEntryItem extends Item {
    public FluidEntryItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        var fluid = FluidEntry.fluidOf(stack);
        return fluid.isEmpty() ? super.getName(stack) : fluid.getHoverName();
    }
}
