package com.kadikular.quantimium.laser;

/**
 * What a lens does to light leaving a Lens Mount. An <b>action</b> gives the light something to do;
 * light can carry several. A <b>modifier</b> changes the actions it carries. See {@link Light} for how
 * they add up along a beam and where light joins.
 */
public enum Lens {
    /** Breaks the block the beam ends on. */
    HARVEST(true, 0xFFF0A040),
    /** Hurts what stands in the beam. */
    DAMAGE(true, 0xFFFF4A4A),
    /** What the actions free, and items lying in the beam, go back up it to a Collector. */
    PICKUP(false, 0xFF5BE38A),
    /** Doubles how far a run of the light reaches. */
    RANGE(false, 0xFF7FB2FF),
    /** Lets the actions it changes act only on what it lists. */
    FILTER(false, 0xFFE8E0C0),
    /** Light no one sees but with a wrench in hand; it stays on the light. */
    UV(false, 0xFF9A7BFF),
    /** Harder cutting (a diamond pickaxe's) and harder hits, for more power. */
    AMPLIFY(false, 0xFFFFE070),
    /** Places blocks a Sender up the beam sends, where the beam ends. */
    PLACE(true, 0xFF4AC8C8),
    /** Its actions work twice as fast: cutting, placing. */
    SPEED(false, 0xFFFFA0D0),
    /** Light passes through blocks; only laser blocks, and blocks that take power, stop it. Costs for each block. */
    PHANTOM(false, 0xFFB8C4D8),
    /** Puts what Senders up the beam send into the inventory it ends on. */
    INSERT(true, 0xFF6FA8FF),
    /** Takes from the inventory it ends on, back up the beam to Collectors. */
    EXTRACT(true, 0xFFFFC46F),
    /** Uses what a Sender sends, or an empty hand, on what the beam ends on: a right click, or a hit. */
    INTERACT(true, 0xFFC0FF7F),
    /** Harvest cuts with Silk Touch. */
    PRECISION(false, 0xFFE0F0FF),
    /** Harvest cuts with Fortune, a level for each, up to three. */
    HARMONIC(false, 0xFFFFD0F0),
    /** Ore cut comes away as Unrealised Matter. */
    UNREALISE(false, 0xFFB070FF),
    /** With Pickup, what comes back up the beam is smelted on the way. */
    SMELT(false, 0xFFFF8040);

    /** The bit a beam's look carries for light that's gone through a rift (not a lens). */
    public static final int MIRRORED_MARK = 30;

    private static final Lens[] VALUES = values();

    public final boolean action;
    /** The tint it gives the beam, ARGB. */
    public final int colour;

    Lens(boolean action, int colour) {
        this.action = action;
        this.colour = colour;
    }

    /** A plain beam's packed lenses (see {@link Light#packed}). */
    public static final int NONE = 0;

    public static boolean has(int packed, Lens lens) {
        return (packed & 1 << lens.ordinal()) != 0;
    }

    /**
     * The beam's tint: its actions' colours mixed, or with none its first modifier's, or 0 for the usual
     * azure.
     */
    public static int colour(int packed) {
        int r = 0, g = 0, b = 0, n = 0;
        for (Lens lens : VALUES) {
            if (!lens.action || !has(packed, lens)) continue;
            r += lens.colour >> 16 & 0xFF;
            g += lens.colour >> 8 & 0xFF;
            b += lens.colour & 0xFF;
            n++;
        }
        if (n > 0) return 0xFF000000 | (r / n) << 16 | (g / n) << 8 | b / n;
        for (Lens lens : VALUES) if (has(packed, lens)) return lens.colour;
        return 0;
    }
}
