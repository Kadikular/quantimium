package com.kadikular.quantimium.reactor;

import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A stock as a plan changes it: what's changed kept apart over what was there, which stays as it was. Kinds it
 * takes the last of read as gone.
 */
public final class StockOverlay extends java.util.AbstractMap<ItemResource, Long> {
    private final Map<ItemResource, Long> base;
    /** What's changed; 0 for gone. */
    private final Map<ItemResource, Long> changed = new java.util.HashMap<>();

    public StockOverlay(Map<ItemResource, Long> base) {
        this.base = base;
    }

    /** What it's laid over, unchanged. */
    public Map<ItemResource, Long> base() {
        return base;
    }

    @Override
    public Long get(Object key) {
        Long now = changed.get(key);
        if (now != null) return now == 0 ? null : now;
        return base.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public Long put(ItemResource key, Long value) {
        Long was = get(key);
        changed.put(key, value);
        return was;
    }

    @Override
    public Long remove(Object key) {
        Long was = get(key);
        if (was != null && key instanceof ItemResource item) changed.put(item, 0L);
        return was;
    }

    /** Kinds there now that weren't in the stock the plan started from. */
    public List<ItemResource> added() {
        List<ItemResource> added = new ArrayList<>();
        changed.forEach((item, amount) -> {
            if (amount != 0 && !base.containsKey(item)) added.add(item);
        });
        return added;
    }

    /** Rarely asked: put together each time. */
    @Override
    public java.util.Set<Map.Entry<ItemResource, Long>> entrySet() {
        Map<ItemResource, Long> now = new LinkedHashMap<>();
        base.forEach((item, amount) -> {
            Long changedTo = changed.get(item);
            if (changedTo == null) now.put(item, amount);
            else if (changedTo != 0) now.put(item, changedTo);
        });
        changed.forEach((item, amount) -> {
            if (amount != 0 && !base.containsKey(item)) now.put(item, amount);
        });
        return now.entrySet();
    }
}

