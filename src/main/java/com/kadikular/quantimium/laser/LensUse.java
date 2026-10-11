package com.kadikular.quantimium.laser;

import org.jetbrains.annotations.Nullable;

/** A lens in a mount: which lens, and for a Filter lens, what it's set to let through. */
public record LensUse(Lens lens, @Nullable LensFilter filter, boolean hits) {
    public LensUse(Lens lens, @Nullable LensFilter filter) {
        this(lens, filter, false);
    }

    public static LensUse of(Lens lens) {
        return new LensUse(lens, null, false);
    }
}
