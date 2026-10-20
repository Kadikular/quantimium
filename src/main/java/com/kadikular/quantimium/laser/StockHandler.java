package com.kadikular.quantimium.laser;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A fixed view of stock, a slot an item: what a machine working out its offers sees through a Tesseract bound to a
 * Logistics Materialiser, instead of the live network. Taking from it changes nothing; putting in takes nothing.
 */
public final class StockHandler implements ResourceHandler<ItemResource> {
    private final List<ItemResource> items;
    private final List<Long> amounts;

    public StockHandler(Map<ItemResource, Long> stock) {
        items = new ArrayList<>(stock.size());
        amounts = new ArrayList<>(stock.size());
        stock.forEach((item, amount) -> {
            if (amount <= 0 || item.isEmpty()) return;
            items.add(item);
            amounts.add(amount);
        });
    }

    /** The same stock, item for item: what a machine was shown before, so it needn't work it out again. */
    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        return other instanceof StockHandler stock && hashCode() == stock.hashCode() && items.equals(stock.items) && amounts.equals(stock.amounts);
    }

    /** Worked out once: a network's thousand items, each hashed with its components, add up. */
    private int hash;
    private boolean hashed;

    @Override
    public int hashCode() {
        if (!hashed) {
            hash = 31 * items.hashCode() + amounts.hashCode();
            hashed = true;
        }
        return hash;
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return index >= 0 && index < amounts.size() ? amounts.get(index) : 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return false;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index < 0 || index >= items.size() || !items.get(index).equals(resource)) return 0;
        return (int) Math.min(amount, amounts.get(index));
    }
}
