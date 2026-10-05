package com.kadikular.quantimium.client;

import com.kadikular.quantimium.recipe.observation.CollapseOutcome;

import java.util.List;
import java.util.function.Consumer;

/**
 * Client-side cache of the server's collapse loot table.
 *
 * <p>The listener exists because the sync and the recipe viewer's own startup race each other: if
 * the data lands first the viewer reads it on load, and if the viewer loads first it registers a
 * listener and gets told later. Deliberately holds no viewer classes so this stays safe to touch
 * without JEI installed.
 */
public final class ClientCollapseOutcomes {

    private static List<CollapseOutcome> outcomes = List.of();
    private static Consumer<List<CollapseOutcome>> listener;

    private ClientCollapseOutcomes() {}

    public static List<CollapseOutcome> get() {
        return outcomes;
    }

    public static void set(List<CollapseOutcome> next) {
        outcomes = List.copyOf(next);
        if (listener != null) {
            listener.accept(outcomes);
        }
    }

    /** Called with the current data immediately, then again whenever a server sends new data. */
    public static void listen(Consumer<List<CollapseOutcome>> next) {
        listener = next;
        if (!outcomes.isEmpty()) {
            next.accept(outcomes);
        }
    }
}
