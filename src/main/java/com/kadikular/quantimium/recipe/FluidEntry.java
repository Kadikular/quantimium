package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Filter entries for fluids. A ghost filter holds items, so a fluid is a hidden item carrying it. A click with a
 * bucket lists the bucket; a right-click lists the water in it, as in AE2.
 */
public final class FluidEntry {
    private FluidEntry() {}

    /** An entry for {@code fluid}. */
    public static ItemStack of(FluidStack fluid) {
        if (fluid.isEmpty()) return ItemStack.EMPTY;
        ItemStack entry = new ItemStack(ModItems.FLUID_ENTRY.get());
        entry.set(ModDataComponents.FLUID_ENTRY.get(), net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluid));
        return entry;
    }

    public static boolean is(ItemStack entry) {
        return entry.is(ModItems.FLUID_ENTRY.get());
    }

    /** The fluid an entry stands for, or empty for an item entry. */
    public static FluidStack fluidOf(ItemStack entry) {
        if (entry.isEmpty() || !is(entry)) return FluidStack.EMPTY;
        var fluid = entry.get(ModDataComponents.FLUID_ENTRY.get());
        return fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : fluid.toStack(net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME);
    }

    /** What a filled container holds; empty for anything else. */
    public static FluidStack contained(ItemStack stack) {
        if (stack.isEmpty() || is(stack)) return fluidOf(stack);
        return net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(stack);
    }

    /**
     * What a click on a ghost entry lists: with a right-click ({@code button} 1), the fluid in what's carried, if it
     * holds one; otherwise the carried item itself.
     */
    public static ItemStack fromClick(ItemStack carried, int button) {
        if (button == 1) {
            FluidStack fluid = contained(carried);
            if (!fluid.isEmpty()) return of(fluid);
        }
        return carried;
    }

    /** For a list that only takes fluids: whatever holds a fluid is listed as it. */
    public static ItemStack asFluid(ItemStack stack) {
        FluidStack fluid = contained(stack);
        return fluid.isEmpty() ? ItemStack.EMPTY : of(fluid);
    }

    public static boolean sameFluid(ItemStack entry, FluidStack fluid) {
        FluidStack listed = fluidOf(entry);
        return !listed.isEmpty() && FluidStack.isSameFluidSameComponents(listed, fluid.copyWithAmount(listed.getAmount()));
    }
}
