package com.kadikular.quantimium.menu;

/**
 * Menu button ids that set a priority outright, so the Priority page can send a typed value in one click.
 * The value rides in the id: {@code BASE + MAX + value}, clear of any menu's own buttons.
 */
public final class PriorityButton {

    public static final int MAX = 999;
    private static final int BASE = 100_000;

    private PriorityButton() {}

    public static int encode(int value) {
        return BASE + MAX + Math.clamp(value, -MAX, MAX);
    }

    public static boolean is(int id) {
        return id >= BASE && id <= BASE + 2 * MAX;
    }

    public static int decode(int id) {
        return id - BASE - MAX;
    }
}
