package com.kadikular.quantimium.unrealised;

import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.world.item.ItemStack;

/** What machines take as Unrealised Matter. */
public final class Matter {

    private Matter() {}

    /**
     * Unrealised Matter, or the Unrealised Ore block that Silk Touch keeps: one block is one Matter
     * with no history, so a silk-touched vein isn't a dead end.
     */
    public static boolean is(ItemStack stack) {
        return stack.is(ModItems.UNREALISED_MATTER.get()) || stack.is(ModBlocks.UNREALISED_ORE.get().asItem());
    }

    /** As {@link #is(ItemStack)}, for what a Reactor holds. */
    public static boolean is(net.neoforged.neoforge.transfer.item.ItemResource item) {
        return item.is(ModItems.UNREALISED_MATTER.get()) || item.is(ModBlocks.UNREALISED_ORE.get().asItem());
    }
}
