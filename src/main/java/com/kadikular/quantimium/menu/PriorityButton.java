package com.kadikular.quantimium.menu;

/**
 * Menu button ids that set a priority outright, so the Priority page can send a typed value in one click.
 * The value rides in the id: {@code BASE + max + value}, clear of any menu's own buttons. {@link #MAX} is the
 * logistics blocks' limit; the ME port's, a storage bus's, is wider.
 */
public final class PriorityButton {

    public static final int MAX = 999;
    private static final int BASE = 100_000;

    private PriorityButton() {}

    public static int encode(int value) {
        return encode(value, MAX);
    }

    public static boolean is(int id) {
        return is(id, MAX);
    }

    public static int decode(int id) {
        return decode(id, MAX);
    }

    public static int encode(int value, int max) {
        return BASE + max + Math.clamp(value, -max, max);
    }

    public static boolean is(int id, int max) {
        return id >= BASE && id <= BASE + 2 * max;
    }

    public static int decode(int id, int max) {
        return id - BASE - max;
    }
}
