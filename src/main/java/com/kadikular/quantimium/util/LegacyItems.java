package com.kadikular.quantimium.util;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Presents the block entities' slot-based automation views (written against the pre-transaction
 * {@link IItemHandler} contract, with their side configuration and per-slot rules) as
 * {@link ResourceHandler}s for the item capability.
 *
 * <p>The wrapped view is asked to do the real thing, and every inventory it can touch is snapshotted
 * first, so an aborted transaction puts the inventories back as they were. A pull from an
 * {@link OnDemandSlots} slot makes what it hands over there and then, inside the transaction: what the
 * craft pulls from elsewhere goes through {@link #legacy} and so joins the same transaction, and the
 * machine's own state (power, and anything else it keeps) is snapshotted with its inventory. An aborted
 * transaction undoes all of it, and one that commits moved exactly what it said.
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

    /**
     * The reverse, for code that still wants the slot-based contract from a neighbour's handler. Each call is a
     * transaction of its own, nested in the one open if there is one: a craft made inside a pull draws on its
     * sources as part of that pull, and an aborted pull puts them back too.
     */
    @Nullable
    public static IItemHandler legacy(@Nullable ResourceHandler<ItemResource> handler) {
        return handler == null ? null : new Nesting(handler);
    }

    /** A transaction for one slot-based call: inside the open one, or a root of its own. */
    private static Transaction open() {
        return Transaction.getLifecycle() == Transaction.Lifecycle.OPEN
                ? Transaction.open(Transaction.getCurrentOpenedTransaction()) : Transaction.openRoot();
    }

    private record Nesting(ResourceHandler<ItemResource> handler) implements IItemHandler {
        @Override
        public int getSlots() {
            return handler.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemResource resource = handler.getResource(slot);
            return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            try (Transaction transaction = open()) {
                int in = handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
                if (!simulate) transaction.commit();
                return in >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - in);
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemResource resource = handler.getResource(slot);
            if (amount <= 0 || resource.isEmpty()) return ItemStack.EMPTY;
            try (Transaction transaction = open()) {
                int got = handler.extract(slot, resource, Math.min(amount, resource.getMaxStackSize()), transaction);
                if (!simulate) transaction.commit();
                return got <= 0 ? ItemStack.EMPTY : resource.toStack(got);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return (int) Math.min(Integer.MAX_VALUE, handler.getCapacityAsLong(slot, ItemResource.EMPTY));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return handler.isValid(slot, ItemResource.of(stack));
        }
    }

    private static final class Adapter implements ResourceHandler<ItemResource> {
        private final IItemHandler view;
        private final ItemStackHandler[] backing;
        private final Journal journal = new Journal();

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
                // Made now, inside the transaction: the machine is snapshotted first, and what the craft draws on
                // elsewhere joins this transaction, so an abort undoes the lot.
                journal.updateSnapshots(transaction);
                return view.extractItem(index, amount, false).getCount();
            }
            if (view.extractItem(index, amount, true).isEmpty()) return 0;
            journal.updateSnapshots(transaction);
            return view.extractItem(index, amount, false).getCount();
        }

        /** The inventories, and for a machine that makes on demand, its own state (power, and the like). */
        private record Snapshot(NonNullList<ItemStack>[] stacks, @Nullable Object state) {}

        private final class Journal extends SnapshotJournal<Snapshot> {
            @Override
            @SuppressWarnings("unchecked")
            protected Snapshot createSnapshot() {
                NonNullList<ItemStack>[] copies = new NonNullList[backing.length];
                for (int i = 0; i < backing.length; i++) copies[i] = backing[i].snapshotStacks();
                return new Snapshot(copies, view instanceof OnDemandSlots onDemand ? onDemand.saveState() : null);
            }

            @Override
            protected void revertToSnapshot(Snapshot snapshot) {
                for (int i = 0; i < backing.length; i++) backing[i].restoreStacks(snapshot.stacks()[i]);
                if (view instanceof OnDemandSlots onDemand) {
                    onDemand.restoreState(snapshot.state());
                    com.kadikular.quantimium.recipe.CraftChain.settled();
                }
            }

            @Override
            protected void onRootCommit(Snapshot original) {
                if (view instanceof OnDemandSlots onDemand) {
                    onDemand.committed();
                    com.kadikular.quantimium.recipe.CraftChain.settled();
                }
            }
        }
    }
}
