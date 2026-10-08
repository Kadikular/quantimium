package com.kadikular.quantimium.laser;

import java.util.List;

/**
 * What a lens does to a beam. A beam's end does one <b>action</b>, changed by any number of
 * <b>modifiers</b>. Lenses go in a Lens Mount; light leaving a mount carries every lens it's met on the
 * way, from this mount and any before it. Where two actions meet, the later one wins.
 *
 * <p>A beam's lenses travel packed in an int: one bit for each modifier, and the action, plus one,
 * from {@link #ACTION_SHIFT} up (0 for none).
 */
public enum Lens {
    /** Breaks the block the beam ends on. */
    HARVEST(true, 0xFFF0A040),
    /** Hurts what stands in the beam. */
    DAMAGE(true, 0xFFFF4A4A),
    /** What the action frees, and items lying in the beam, go back up it to a Collector. */
    PICKUP(false, 0xFF5BE38A);

    private static final int ACTION_SHIFT = 16;
    private static final Lens[] VALUES = values();

    public final boolean action;
    /** The tint it gives the beam, ARGB. */
    public final int colour;

    Lens(boolean action, int colour) {
        this.action = action;
        this.colour = colour;
    }

    /** No lenses at all: a plain beam. */
    public static final int NONE = 0;

    /** The packed lenses of a beam that's met {@code incoming}, then the lenses in order. */
    public static int after(int incoming, List<Lens> lenses) {
        int packed = incoming;
        for (Lens lens : lenses) {
            if (lens.action) packed = (packed & ((1 << ACTION_SHIFT) - 1)) | (lens.ordinal() + 1) << ACTION_SHIFT;
            else packed |= 1 << lens.ordinal();
        }
        return packed;
    }

    /** Two beams' lenses where they join: every modifier of either, and the one action (the higher, if both have one). */
    public static int join(int a, int b) {
        int modifiers = (a | b) & ((1 << ACTION_SHIFT) - 1);
        return modifiers | Math.max(a >>> ACTION_SHIFT, b >>> ACTION_SHIFT) << ACTION_SHIFT;
    }

    /** The action a beam's lenses give its end, or null for none. */
    public static Lens action(int packed) {
        int action = packed >>> ACTION_SHIFT;
        return action <= 0 || action > VALUES.length ? null : VALUES[action - 1];
    }

    public static boolean has(int packed, Lens modifier) {
        return !modifier.action ? (packed & 1 << modifier.ordinal()) != 0 : action(packed) == modifier;
    }

    /** Whether a beam with these lenses does anything beyond carrying power. */
    public static boolean acts(int packed) {
        return packed != NONE;
    }

    /** The beam's tint: its action's colour, or its first modifier's, or 0 for the usual azure. */
    public static int colour(int packed) {
        Lens action = action(packed);
        if (action != null) return action.colour;
        for (Lens lens : VALUES) if (has(packed, lens)) return lens.colour;
        return 0;
    }
}
