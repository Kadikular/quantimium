package com.kadikular.quantimium.phase;

import com.mojang.serialization.Codec;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flux rifts for one dimension. Unlike {@link WorldRiftData} this is saved: a rift that healed on
 * relog would be a free exit from the whole mechanic.
 */
public final class FluxRiftData {

    public static final Codec<FluxRiftData> CODEC = FluxRift.CODEC.listOf()
            .xmap(FluxRiftData::new, data -> List.copyOf(data.rifts.values()));

    private final Map<UUID, FluxRift> rifts = new LinkedHashMap<>();

    public FluxRiftData() {}

    private FluxRiftData(List<FluxRift> saved) {
        for (FluxRift rift : saved) rifts.put(rift.id(), rift);
    }

    public Collection<FluxRift> rifts() {
        return rifts.values();
    }

    public FluxRift get(UUID id) {
        return rifts.get(id);
    }

    public void add(FluxRift rift) {
        rifts.put(rift.id(), rift);
    }

    public void remove(UUID id) {
        rifts.remove(id);
    }

    public boolean isEmpty() {
        return rifts.isEmpty();
    }
}
