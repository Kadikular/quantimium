package com.kadikular.quantimium.fold;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The fold adapters the chamber knows. Ours is built in; a partner mod's is added by its compat
 * module when that mod is loaded.
 */
public final class FoldAdapters {

    private static final List<FoldAdapter> ADAPTERS = new CopyOnWriteArrayList<>(List.of(new FoundryFoldAdapter()));

    private FoldAdapters() {}

    public static void register(FoldAdapter adapter) {
        if (byId(adapter.id()).isEmpty()) ADAPTERS.add(adapter);
    }

    public static Optional<FoldAdapter> byId(Identifier id) {
        for (FoldAdapter adapter : ADAPTERS) {
            if (adapter.id().equals(id)) return Optional.of(adapter);
        }
        return Optional.empty();
    }

    public static Optional<FoldAdapter> forController(BlockState state) {
        for (FoldAdapter adapter : ADAPTERS) {
            if (adapter.handles(state)) return Optional.of(adapter);
        }
        return Optional.empty();
    }
}
