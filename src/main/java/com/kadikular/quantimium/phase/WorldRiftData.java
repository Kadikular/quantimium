package com.kadikular.quantimium.phase;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory tears for one dimension. Not written to disk — they are session geometry. */
public final class WorldRiftData {

    private final Map<UUID, WorldRift> rifts = new LinkedHashMap<>();

    public Collection<WorldRift> rifts() {
        return rifts.values();
    }

    public void add(WorldRift rift) {
        rifts.put(rift.id(), rift);
    }

    public void remove(UUID id) {
        rifts.remove(id);
    }

    public WorldRift ownedReturn(UUID owner) {
        for (WorldRift rift : rifts.values()) {
            if (rift.kind() == MirrorRiftKind.RETURN && owner.equals(rift.owner())) {
                return rift;
            }
        }
        return null;
    }
}
