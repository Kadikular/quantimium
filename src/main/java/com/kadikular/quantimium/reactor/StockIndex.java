package com.kadikular.quantimium.reactor;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;

/**
 * Where a stock's kinds are by item, kept as it changes: what a plan looks up an ingredient's matches in, rather than
 * testing every kind a Reactor holds.
 */
public interface StockIndex {
    /** The kinds held of {@code item} (with whatever components), in the order they came in. */
    List<ItemResource> kindsOf(Item item);

    /** Where {@code kind} stands in the order kinds came in: earlier first. */
    long order(ItemResource kind);
}
