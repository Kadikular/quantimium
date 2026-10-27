package com.kadikular.quantimium.laser;

import org.jetbrains.annotations.Nullable;

/**
 * A lens in a mount: which lens, for a filter lens what it lets through, for an Isolator Lens what it passes, and for
 * an Interact lens its settings packed in {@code interact}: {@link #HITS}, and what it works on, two bits above; for a
 * Redstone Lens, its settings.
 */
public record LensUse(Lens lens, @Nullable LensFilter filter, int interact, @Nullable IsolatorSettings isolator, @Nullable RedstoneSettings redstone) {
    /** An Interact lens set to hit (a left click). */
    public static final int HITS = 1;
    /** A Void lens set to void everything, not only what nothing has room for: carried on the streams it changes. */
    public static final int VOID_ALL = 1 << 3;
    /** What an Interact lens works on: both blocks and what's living, blocks only, or living things only. */
    public static final int BOTH = 0, BLOCKS = 1, ENTITIES = 2;

    public LensUse(Lens lens, @Nullable LensFilter filter, int interact, @Nullable IsolatorSettings isolator) {
        this(lens, filter, interact, isolator, null);
    }

    public LensUse(Lens lens, @Nullable LensFilter filter, boolean hits) {
        this(lens, filter, hits ? HITS : 0, null);
    }

    public LensUse(Lens lens, @Nullable LensFilter filter) {
        this(lens, filter, false);
    }

    public boolean hits() {
        return (interact & HITS) != 0;
    }

    /** What packed Interact settings work on. */
    public static int targets(int interact) {
        return interact >> 1 & 3;
    }

    public static int pack(boolean hits, int targets) {
        return (hits ? HITS : 0) | (targets & 3) << 1;
    }

    public static LensUse of(Lens lens) {
        return new LensUse(lens, null, false);
    }

    /** What the lens in {@code stack} does, with its settings. */
    public static LensUse of(net.minecraft.world.item.ItemStack stack, Lens lens) {
        int settings = lens == Lens.VOID
                ? (com.kadikular.quantimium.item.VoidLensItem.voidsAll(stack) ? VOID_ALL : 0)
                : pack(Boolean.TRUE.equals(stack.get(com.kadikular.quantimium.init.ModDataComponents.INTERACT_HITS.get())),
                        com.kadikular.quantimium.item.InteractLensItem.targets(stack));
        return new LensUse(lens, stack.get(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get()), settings,
                stack.get(com.kadikular.quantimium.init.ModDataComponents.ISOLATOR.get()),
                lens == Lens.REDSTONE ? com.kadikular.quantimium.item.RedstoneLensItem.settingsOf(stack) : null);
    }
}
