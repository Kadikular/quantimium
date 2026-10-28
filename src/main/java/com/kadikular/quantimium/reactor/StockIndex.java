package com.kadikular.quantimium.reactor;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a stock's kinds are by item, kept as it changes: what a plan looks up an ingredient's matches in, rather than
 * testing every kind a Reactor holds.
 */
public interface StockIndex {
    /** The kinds held of {@code item} (with whatever components), in the order they came in. */
    List<ItemResource> kindsOf(Item item);

    /** Where {@code kind} stands in the order kinds came in: earlier first. */
    long order(ItemResource kind);

    /**
     * {@code stock}'s kinds as they are now, for planning against it many times: it isn't kept up to date, so a kind
     * added to the stock later must be indexed by whoever adds it.
     */
    static StockIndex of(Map<ItemResource, Long> stock) {
        Map<Item, List<ItemResource>> byItem = new HashMap<>();
        Map<ItemResource, Long> order = ItemKeys.map();
        for (ItemResource kind : stock.keySet()) {
            order.put(kind, (long) order.size());
            byItem.computeIfAbsent(kind.getItem(), k -> new ArrayList<>(1)).add(kind);
        }
        return new StockIndex() {
            @Override
            public List<ItemResource> kindsOf(Item item) {
                return byItem.getOrDefault(item, List.of());
            }

            @Override
            public long order(ItemResource kind) {
                return order.getOrDefault(kind, Long.MAX_VALUE);
            }
        };
    }
}
