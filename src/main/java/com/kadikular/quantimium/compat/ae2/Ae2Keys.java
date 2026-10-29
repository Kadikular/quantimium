package com.kadikular.quantimium.compat.ae2;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.kadikular.quantimium.recipe.FluidEntry;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * Between what a Reactor keeps by item (a fluid kept as its fluid entry, counted in mB) and AE2's keys (a fluid as an
 * {@link AEFluidKey}, counted in mB too).
 */
public final class Ae2Keys {
    private Ae2Keys() {}

    /** The AE2 key for {@code item}: a fluid's for a fluid entry, an item's otherwise. */
    public static AEKey of(ItemResource item) {
        return FluidEntry.is(item) ? AEFluidKey.of(FluidEntry.fluid(item)) : AEItemKey.of(item);
    }

    /** What a Reactor keeps {@code key} under, or null for neither an item nor a fluid. */
    @Nullable
    public static ItemResource resourceOf(AEKey key) {
        if (key instanceof AEItemKey item) return item.toResource();
        if (key instanceof AEFluidKey fluid) return FluidEntry.key(fluid.toResource());
        return null;
    }
}
