package com.kadikular.quantimium.laser;

import org.jetbrains.annotations.Nullable;

/** A lens in a mount: which lens, for a filter lens what it lets through, for an Isolator Lens what it passes. */
public record LensUse(Lens lens, @Nullable LensFilter filter, boolean hits, @Nullable IsolatorSettings isolator) {
    public LensUse(Lens lens, @Nullable LensFilter filter, boolean hits) {
        this(lens, filter, hits, null);
    }

    public LensUse(Lens lens, @Nullable LensFilter filter) {
        this(lens, filter, false);
    }

    public static LensUse of(Lens lens) {
        return new LensUse(lens, null, false);
    }

    /** What the lens in {@code stack} does, with its settings. */
    public static LensUse of(net.minecraft.world.item.ItemStack stack, Lens lens) {
        return new LensUse(lens, stack.get(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get()),
                Boolean.TRUE.equals(stack.get(com.kadikular.quantimium.init.ModDataComponents.INTERACT_HITS.get())),
                stack.get(com.kadikular.quantimium.init.ModDataComponents.ISOLATOR.get()));
    }
}
