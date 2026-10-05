package com.kadikular.quantimium.util;

import com.kadikular.quantimium.Quantimium;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Presents the block entities' slot-based automation views (written against the pre-transaction
 * {@link IItemHandler} contract, with their side configuration and per-slot rules) as
 * {@link ResourceHandler}s for the item capability.
 *
 * <p>The wrapped view is asked to do the real thing, and every inventory it can touch is snapshotted
 * first, so an aborted transaction puts the inventories back as they were. Pulls from
 * {@link OnDemandSlots} are the exception: they answer from a simulation and happen when the root
 * transaction commits, so a transaction that only asks (as {@code IItemHandler.of} simulates) costs
 * nothing.
 */
@SuppressWarnings("deprecation")
public final class LegacyItems {
    private static final Map<IItemHandler, ResourceHandler<ItemResource>> CACHE = new WeakHashMap<>();

    private LegacyItems() {}

    @Nullable
    public static synchronized ResourceHandler<ItemResource> of(@Nullable IItemHandler view, ItemStackHandler... backing) {
        if (view == null) return null;
        return CACHE.computeIfAbsent(view, v -> new Adapter(v, backing));
    }

    /** The reverse, for code that still wants the slot-based contract from a neighbour's handler. */
    @Nullable
    public static IItemHandler legacy(@Nullable ResourceHandler<ItemResource> handler) {
        return handler == null ? null : IItemHandler.of(handler);
    }

    private static final class Adapter implements ResourceHandler<ItemResource> {
        private final IItemHandler view;
        private final ItemStackHandler[] backing;
        private final Journal journal = new Journal();
        private final Deferred deferred = new Deferred();

        private Adapter(IItemHandler view, ItemStackHandler[] backing) {
            this.view = view;
            this.backing = backing;
        }

        @Override
        public int size() {
            return view.getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(view.getStackInSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return view.getStackInSlot(index).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            if (!resource.isEmpty() && !view.isItemValid(index, resource.toStack(1))) return 0;
            int limit = view.getSlotLimit(index);
            return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return view.isItemValid(index, resource.toStack(1));
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            ItemStack offered = resource.toStack(amount);
            // Ask first, so an insert that changes nothing does not open a snapshot.
            if (view.insertItem(index, offered, true).getCount() >= amount) return 0;
            journal.updateSnapshots(transaction);
            ItemStack remainder = view.insertItem(index, offered, false);
            return amount - remainder.getCount();
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (!resource.matches(view.getStackInSlot(index))) return 0;
            if (view instanceof OnDemandSlots onDemand && onDemand.producesOnDemand(index)) {
                // Promise what a pull would give, less what this transaction already promised from
                // the slot, and do the pull once the root commits.
                int promised = deferred.pending(index);
                int available = view.extractItem(index, amount + promised, true).getCount() - promised;
                int taken = Math.min(amount, available);
                if (taken <= 0) return 0;
                deferred.updateSnapshots(transaction);
                deferred.pulls.add(new int[] {index, taken});
                return taken;
            }
            if (view.extractItem(index, amount, true).isEmpty()) return 0;
            journal.updateSnapshots(transaction);
            return view.extractItem(index, amount, false).getCount();
        }

        /** Pulls from on-demand slots, waiting for the root transaction to commit. */
        private final class Deferred extends SnapshotJournal<List<int[]>> {
            private final List<int[]> pulls = new ArrayList<>();

            int pending(int index) {
                int total = 0;
                for (int[] pull : pulls) if (pull[0] == index) total += pull[1];
                return total;
            }

            @Override
            protected List<int[]> createSnapshot() {
                return new ArrayList<>(pulls);
            }

            @Override
            protected void revertToSnapshot(List<int[]> snapshot) {
                pulls.clear();
                pulls.addAll(snapshot);
            }

            @Override
            protected void onRootCommit(List<int[]> originalState) {
                List<int[]> due = List.copyOf(pulls);
                pulls.clear();
                for (int[] pull : due) {
                    int got = view.extractItem(pull[0], pull[1], false).getCount();
                    if (got < pull[1]) {
                        Quantimium.LOGGER.warn("An on-demand pull from slot {} promised {} but gave {}", pull[0], pull[1], got);
                    }
                }
            }
        }

        private final class Journal extends SnapshotJournal<NonNullList<ItemStack>[]> {
            @Override
            @SuppressWarnings("unchecked")
            protected NonNullList<ItemStack>[] createSnapshot() {
                NonNullList<ItemStack>[] copies = new NonNullList[backing.length];
                for (int i = 0; i < backing.length; i++) copies[i] = backing[i].snapshotStacks();
                return copies;
            }

            @Override
            protected void revertToSnapshot(NonNullList<ItemStack>[] snapshot) {
                for (int i = 0; i < backing.length; i++) backing[i].restoreStacks(snapshot[i]);
            }
        }
    }
}
