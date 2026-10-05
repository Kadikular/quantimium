package com.kadikular.quantimium.util;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Bridges between the fluid handler contract that fills and drains by fluid, and {@link ResourceHandler}s
 * with transactions, in both directions.
 */
@SuppressWarnings("deprecation")
public final class LegacyFluids {
    private static final Map<IFluidHandler, ResourceHandler<FluidResource>> CACHE = new WeakHashMap<>();

    private LegacyFluids() {}

    /** The fill/drain contract over a neighbour's {@link ResourceHandler}, for code written against it. */
    @Nullable
    public static IFluidHandler legacy(@Nullable ResourceHandler<FluidResource> handler) {
        return handler == null ? null : IFluidHandler.of(handler);
    }

    /**
     * Presents a fill/drain view (with its side rules) as a {@link ResourceHandler}. The view is asked to do
     * the real thing, and {@code snapshot}/{@code restore} put the tanks back if the transaction aborts.
     */
    @Nullable
    public static synchronized ResourceHandler<FluidResource> of(@Nullable IFluidHandler view,
                                                                 Supplier<FluidStack[]> snapshot,
                                                                 Consumer<FluidStack[]> restore) {
        if (view == null) return null;
        return CACHE.computeIfAbsent(view, v -> new Adapter(v, snapshot, restore));
    }

    private static final class Adapter implements ResourceHandler<FluidResource> {
        private final IFluidHandler view;
        private final Journal journal;

        private Adapter(IFluidHandler view, Supplier<FluidStack[]> snapshot, Consumer<FluidStack[]> restore) {
            this.view = view;
            this.journal = new Journal(snapshot, restore);
        }

        @Override
        public int size() {
            return view.getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.of(view.getFluidInTank(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return view.getFluidInTank(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            if (!resource.isEmpty() && !view.isFluidValid(index, resource.toStack(1))) return 0;
            return view.getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return view.isFluidValid(index, resource.toStack(1));
        }

        /** The tank a fill of {@code resource} would land in: the one holding it, else the first empty one that accepts it. */
        private int fillTarget(FluidResource resource) {
            int empty = -1;
            for (int tank = 0; tank < view.getTanks(); tank++) {
                FluidStack held = view.getFluidInTank(tank);
                if (held.isEmpty()) {
                    if (empty < 0 && view.isFluidValid(tank, resource.toStack(1))) empty = tank;
                } else if (resource.matches(held)) {
                    return tank;
                }
            }
            return empty;
        }

        private int drainSource(FluidResource resource) {
            for (int tank = 0; tank < view.getTanks(); tank++) {
                if (resource.matches(view.getFluidInTank(tank))) return tank;
            }
            return -1;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == fillTarget(resource) ? insert(resource, amount, transaction) : 0;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            FluidStack offered = resource.toStack(amount);
            if (view.fill(offered, IFluidHandler.FluidAction.SIMULATE) <= 0) return 0;
            journal.updateSnapshots(transaction);
            return view.fill(offered, IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == drainSource(resource) ? extract(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            FluidStack wanted = resource.toStack(amount);
            if (view.drain(wanted, IFluidHandler.FluidAction.SIMULATE).isEmpty()) return 0;
            journal.updateSnapshots(transaction);
            return view.drain(wanted, IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
    }

    private static final class Journal extends SnapshotJournal<FluidStack[]> {
        private final Supplier<FluidStack[]> snapshot;
        private final Consumer<FluidStack[]> restore;

        private Journal(Supplier<FluidStack[]> snapshot, Consumer<FluidStack[]> restore) {
            this.snapshot = snapshot;
            this.restore = restore;
        }

        @Override
        protected FluidStack[] createSnapshot() {
            return snapshot.get();
        }

        @Override
        protected void revertToSnapshot(FluidStack[] state) {
            restore.accept(state);
        }
    }
}
