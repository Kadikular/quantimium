package com.kadikular.quantimium.laser;

/**
 * Laser channels: 0 to 15, and {@link #ALL}. A lens works on its slot's channel; ports, tanks, receptacles and
 * Logistics Controllers serve theirs. Channel 0 is the default, so light set to nothing behaves as it always has.
 */
public final class Channels {
    /** Every channel at once: a lens on it changes every channel's streams, a port on it serves every channel. */
    public static final int ALL = 16;
    /** How many settings there are: 0 to 15, and All. */
    public static final int COUNT = 17;

    private Channels() {}

    /** The channel of the lens acting now (0 between): what a Logistics Controller on All up its beam works with. */
    private static int acting;

    public static int acting() {
        return acting;
    }

    /** Runs {@code act} as a lens on {@code channel} acting. */
    static void actingOn(int channel, Runnable act) {
        int was = acting;
        acting = channel;
        try {
            act.run();
        } finally {
            acting = was;
        }
    }

    /** Whether {@code lens} changes the laser itself, whatever its channel: Range (its reach), UV, Phantom. */
    public static boolean wholeLaser(Lens lens) {
        return lens == Lens.RANGE || lens == Lens.UV || lens == Lens.PHANTOM;
    }

    /** A saved or sent channel, kept in range. */
    public static int of(int channel) {
        return channel < 0 || channel > ALL ? 0 : channel;
    }

    /** Whether a lens on {@code lens}'s channel changes a stream on {@code stream}'s: the same, or the lens on All. */
    public static boolean changes(int lens, int stream) {
        return lens == ALL || lens == stream;
    }

    /** Whether something serving {@code served} (a port, a receptacle) takes what's on {@code carried}: either on All, or the same. */
    public static boolean serves(int served, int carried) {
        return served == ALL || carried == ALL || served == carried;
    }

    /** The next setting (0, 1 ... 15, All, 0), or the one before with {@code back}. */
    public static int cycle(int channel, boolean back) {
        return Math.floorMod(of(channel) + (back ? -1 : 1), COUNT);
    }

    /** How it's written: its number, or "All". */
    public static net.minecraft.network.chat.Component label(int channel) {
        return channel == ALL ? net.minecraft.network.chat.Component.translatable("gui.quantimium.channel.all")
                : net.minecraft.network.chat.Component.literal(Integer.toString(channel));
    }
}
