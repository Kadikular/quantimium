package com.kadikular.quantimium.reactor;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Map;

/**
 * Maps keyed by item that hash cheaply. {@link ItemResource#hashCode} hashes every component an item has by default,
 * each time it's asked; a plan asks thousands of times. Two equal resources are the same item with the same changes
 * from its defaults, so hashing just the item and those changes finds the same entries.
 */
public final class ItemKeys {
    private ItemKeys() {}

    public static final Hash.Strategy<ItemResource> STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(ItemResource item) {
            return hash(item);
        }

        @Override
        public boolean equals(ItemResource a, ItemResource b) {
            return a == b || (a != null && a.equals(b));
        }
    };

    public static int hash(ItemResource item) {
        if (item == null) return 0;
        int hash = System.identityHashCode(item.getItem());
        return item.isComponentsPatchEmpty() ? hash : 31 * hash + item.getComponentsPatch().hashCode();
    }

    public static <V> Map<ItemResource, V> map() {
        return new Object2ObjectOpenCustomHashMap<>(STRATEGY);
    }

    /** Kept in the order keys first went in. */
    public static <V> Map<ItemResource, V> linkedMap() {
        return new Object2ObjectLinkedOpenCustomHashMap<>(STRATEGY);
    }
}
