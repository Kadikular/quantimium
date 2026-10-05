package com.kadikular.quantimium.phase;

/**
 * Marks Quantimium-owned overlay edits (recede, host collapse) so {@code destroyBlock} may proceed.
 * Annihilation planes and other machines call {@code destroyBlock} without going through
 * {@code BreakEvent} / {@code onDestroyedByPlayer}.
 */
public final class OverlayBlockEdit {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private OverlayBlockEdit() {}

    public static boolean allowed() {
        return DEPTH.get() > 0;
    }

    public static void allow(Runnable action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }
}
