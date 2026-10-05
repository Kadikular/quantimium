package com.kadikular.quantimium.util;

import java.util.function.Predicate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * One-way and filtered views of an inventory or tank set, for pipes and hoppers. Every operation goes
 * straight through to the backing handler on the caller's transaction.
 */
public final class RestrictedItems {
    private RestrictedItems() {}

    /** Items come out, nothing goes in. */
    public static ResourceHandler<ItemResource> takeOnly(ResourceHandler<ItemResource> backing) {
        return filter(backing, resource -> false, true);
    }

    /** Items the filter accepts go in, nothing comes out. */
    public static ResourceHandler<ItemResource> insertOnly(ResourceHandler<ItemResource> backing,
                                                           Predicate<ItemResource> accepts) {
        return filter(backing, accepts, false);
    }

    /** Insertion of whatever {@code accepts} lets through, extraction only if {@code extractable}. */
    public static <T extends Resource> ResourceHandler<T> filter(ResourceHandler<T> backing,
                                                                 Predicate<T> accepts, boolean extractable) {
        return new View<>(backing, accepts, extractable);
    }

    /** Insertion and extraction each allowed or not, as a side mode says. */
    public static <T extends Resource> ResourceHandler<T> byMode(ResourceHandler<T> backing,
                                                                 boolean allowInput, boolean allowOutput) {
        return filter(backing, resource -> allowInput, allowOutput);
    }

    private record View<T extends Resource>(ResourceHandler<T> backing, Predicate<T> accepts,
                                            boolean extractable) implements ResourceHandler<T> {
        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public T getResource(int index) {
            return backing.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return backing.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, T resource) {
            return resource.isEmpty() || accepts.test(resource) ? backing.getCapacityAsLong(index, resource) : 0;
        }

        @Override
        public boolean isValid(int index, T resource) {
            return accepts.test(resource) && backing.isValid(index, resource);
        }

        @Override
        public int insert(int index, T resource, int amount, TransactionContext transaction) {
            return accepts.test(resource) ? backing.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, T resource, int amount, TransactionContext transaction) {
            return extractable ? backing.extract(index, resource, amount, transaction) : 0;
        }
    }
}
