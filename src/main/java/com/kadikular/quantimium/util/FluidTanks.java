package com.kadikular.quantimium.util;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.List;

/**
 * A row of tanks, one fluid in each: a fluid goes into the tank already holding it, else the first empty one, and is
 * never split over two. What a Laser Tank holds; a Crafter's input tanks can be the same.
 */
@SuppressWarnings("deprecation")
public final class FluidTanks {
    private final FluidStack[] tanks;
    private final int capacity;
    private final Runnable onChanged;

    public FluidTanks(int count, int capacity, Runnable onChanged) {
        this.tanks = new FluidStack[count];
        this.capacity = capacity;
        this.onChanged = onChanged;
        for (int i = 0; i < count; i++) tanks[i] = FluidStack.EMPTY;
    }

    public int size() {
        return tanks.length;
    }

    public int capacity() {
        return capacity;
    }

    public FluidStack get(int tank) {
        return tank >= 0 && tank < tanks.length ? tanks[tank] : FluidStack.EMPTY;
    }

    public void set(int tank, FluidStack stack) {
        if (tank < 0 || tank >= tanks.length) return;
        tanks[tank] = stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
        onChanged.run();
    }

    /** Fills what it can of {@code fluid}; how much. */
    public int fill(FluidStack fluid, boolean simulate) {
        if (fluid.isEmpty()) return 0;
        int target = -1;
        for (int i = 0; i < tanks.length; i++) {
            if (!tanks[i].isEmpty() && FluidStack.isSameFluidSameComponents(tanks[i], fluid)) {
                target = i;
                break;
            }
            if (tanks[i].isEmpty() && target < 0) target = i;
        }
        if (target < 0) return 0;
        FluidStack there = tanks[target];
        int filled = Math.min(capacity - there.getAmount(), fluid.getAmount());
        if (filled <= 0) return 0;
        if (!simulate) {
            tanks[target] = there.isEmpty() ? fluid.copyWithAmount(filled) : there.copyWithAmount(there.getAmount() + filled);
            onChanged.run();
        }
        return filled;
    }

    /** Drains up to {@code max} of tank {@code tank}. */
    public FluidStack drain(int tank, int max, boolean simulate) {
        FluidStack there = get(tank);
        if (there.isEmpty() || max <= 0) return FluidStack.EMPTY;
        int drained = Math.min(max, there.getAmount());
        if (!simulate) {
            tanks[tank] = drained == there.getAmount() ? FluidStack.EMPTY : there.copyWithAmount(there.getAmount() - drained);
            onChanged.run();
        }
        return there.copyWithAmount(drained);
    }

    /** Drains up to {@code fluid}'s amount of that fluid, from whichever tank holds it. */
    public FluidStack drain(FluidStack fluid, boolean simulate) {
        if (fluid.isEmpty()) return FluidStack.EMPTY;
        for (int i = 0; i < tanks.length; i++) {
            if (FluidStack.isSameFluidSameComponents(tanks[i], fluid)) return drain(i, fluid.getAmount(), simulate);
        }
        return FluidStack.EMPTY;
    }

    public boolean isEmpty() {
        for (FluidStack tank : tanks) if (!tank.isEmpty()) return false;
        return true;
    }

    public List<FluidStack> contents() {
        List<FluidStack> all = new ArrayList<>(tanks.length);
        for (FluidStack tank : tanks) all.add(tank.copy());
        return all;
    }

    /** Puts back {@link #contents} as saved, without telling anyone. */
    public void load(List<FluidStack> saved) {
        for (int i = 0; i < tanks.length; i++) tanks[i] = i < saved.size() ? saved.get(i).copy() : FluidStack.EMPTY;
    }

    public FluidStack[] snapshot() {
        FluidStack[] copy = new FluidStack[tanks.length];
        for (int i = 0; i < tanks.length; i++) copy[i] = tanks[i].copy();
        return copy;
    }

    public void restore(FluidStack[] snapshot) {
        for (int i = 0; i < tanks.length && i < snapshot.length; i++) tanks[i] = snapshot[i];
    }

    /** The tanks to pipes: they fill only if {@code fills}, and drain only if {@code drains}. */
    public IFluidHandler view(java.util.function.BooleanSupplier fills, java.util.function.BooleanSupplier drains) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return tanks.length;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return get(tank);
            }

            @Override
            public int getTankCapacity(int tank) {
                return capacity;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return fills.getAsBoolean();
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return fills.getAsBoolean() ? FluidTanks.this.fill(resource, action.simulate()) : 0;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return drains.getAsBoolean() ? FluidTanks.this.drain(resource, action.simulate()) : FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                if (!drains.getAsBoolean()) return FluidStack.EMPTY;
                for (int i = 0; i < tanks.length; i++) {
                    if (!tanks[i].isEmpty()) return FluidTanks.this.drain(i, maxDrain, action.simulate());
                }
                return FluidStack.EMPTY;
            }
        };
    }

    /** Tank {@code tank} alone, filled and drained freely: what a bucket clicked on it in a screen works with. */
    public ResourceHandler<FluidResource> tank(int tank) {
        if (single == null) {
            single = new IFluidHandler[tanks.length];
            for (int i = 0; i < tanks.length; i++) {
                int index = i;
                single[i] = new IFluidHandler() {
                    @Override
                    public int getTanks() {
                        return 1;
                    }

                    @Override
                    public FluidStack getFluidInTank(int t) {
                        return get(index);
                    }

                    @Override
                    public int getTankCapacity(int t) {
                        return capacity;
                    }

                    @Override
                    public boolean isFluidValid(int t, FluidStack stack) {
                        return get(index).isEmpty() || FluidStack.isSameFluidSameComponents(get(index), stack);
                    }

                    @Override
                    public int fill(FluidStack resource, FluidAction action) {
                        if (!isFluidValid(0, resource)) return 0;
                        FluidStack there = get(index);
                        int filled = Math.min(capacity - there.getAmount(), resource.getAmount());
                        if (filled <= 0) return 0;
                        if (action.execute()) set(index, there.isEmpty() ? resource.copyWithAmount(filled) : there.copyWithAmount(there.getAmount() + filled));
                        return filled;
                    }

                    @Override
                    public FluidStack drain(FluidStack resource, FluidAction action) {
                        return FluidStack.isSameFluidSameComponents(get(index), resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
                    }

                    @Override
                    public FluidStack drain(int maxDrain, FluidAction action) {
                        return FluidTanks.this.drain(index, maxDrain, action.simulate());
                    }
                };
            }
        }
        return handler(single[tank]);
    }

    private IFluidHandler[] single;

    /** {@link #view} as a {@link ResourceHandler}, undone with the transaction it's used in. */
    public ResourceHandler<FluidResource> handler(IFluidHandler view) {
        return LegacyFluids.of(view, this::snapshot, this::restore);
    }
}
